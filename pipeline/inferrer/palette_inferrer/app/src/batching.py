import asyncio
from uuid import UUID, uuid4
from typing import Callable, Dict, Generic, List, TypeVar
from async_timeout import timeout
from src.logging import get_logger


logger = get_logger(__name__)

Input = TypeVar("Input")
Result = TypeVar("Result")


class BatchExecutionQueue(Generic[Input, Result]):
    """
    This class is a helper for batching calls to some processing function, calling that function
    when a given batch sized is reached or when a given timeout occurs - whichever happens first.

    Once instantiated, the user must call `start_worker` when an asyncio event loop is running.
    After this, calls to `execute` will be queued into batches internally, and will return
    asynchronously with the corresponding result. Any errors will cause the whole batch to fail.
    The user should call `stop_worker` when done with the instance.

    Args:
        sync_batch_processor:
            A synchronous function that takes a list of inputs and returns a list of corresponding
            results **in the same order**.
        batch_size: The maximum size of a batch before it is processed.
        timeout: The time (in seconds) after which an unfilled batch must be processed.
    """

    def __init__(
        self,
        sync_batch_processor: Callable[[List[Input]], List[Result]],
        batch_size: int,
        timeout: float,
    ):
        self.sync_batch_processor = sync_batch_processor
        self.batch_size = batch_size
        self.timeout = timeout
        self.queue = asyncio.Queue(batch_size)
        self.output: Dict[UUID, Result] = {}

    def __del__(self):
        self.stop_worker()

    def start_worker(self) -> None:
        """This must be called before `execute` can be used, and while an event loop is running"""
        self.task = asyncio.create_task(self.__worker())

    def stop_worker(self) -> None:
        """Should be called when the instance is no longer required"""
        self.task.cancel()
        del self.task

    async def execute(self, input: Generic[Input]) -> Generic[Result]:
        """When called with a single input to the batch processor, this asynchronously returns the
        associated output, or raises an exception if the batch processing failed."""

        # We can't guarantee that coroutines that are waiting on `self.queue.join()` will
        # resolve in the same order that they joined, so we need a way to match calls to
        # `execute` with batch processor outputs, even though the processor is order-preserving.
        #
        # So that we don't have to constrain the input to be a hashable object, or rely
        # on complex and confusing counters alongside the queue, we instead generate a UUID
        # for each call, and use that as a key for looking up outputs.
        execution_id = uuid4()
        await self.queue.put((execution_id, input))

        # wait for the queue to be processed OR for an error in the worker to occur
        queue_processed = asyncio.create_task(self.queue.join())
        try:
            done, _ = await asyncio.wait(
                [queue_processed, self.task], return_when=asyncio.FIRST_COMPLETED
            )
        except AttributeError as e:
            logger.error("Execution attempted before worker was started")
            raise e

        if queue_processed in done and execution_id in self.output:
            return self.output.pop(execution_id)
        else:
            raise self.task.exception()

    def __mark_queue_done(self, n) -> None:
        """Calls to `execute` will only resolve when `task_done()` has been called for every
        task in the queue."""
        [self.queue.task_done() for _ in range(n)]

    async def __worker(self):
        """The worker runs continuously, blocking asynchronously until it has received items
        to process. If an exception is raised in here then the task will stop silently."""

        # This implementation is based upon:
        # http://blog.mathieu-leplatre.info/some-python-3-asyncio-snippets.html#consume-queue-in-batches
        while True:
            batch: List[(UUID, Input)] = []
            try:
                with timeout(self.timeout):
                    while len(batch) < self.batch_size:
                        item = await self.queue.get()
                        batch.append(item)
            except asyncio.TimeoutError:
                pass
            except Exception as e:
                logger.error("Unexpected error consuming queue", e)
                raise e

            if batch:
                try:
                    ids, inputs = zip(*batch)
                    results: List[Result] = self.sync_batch_processor(inputs)
                    assert len(results) == len(inputs)
                    self.output.update(dict(zip(ids, results)))
                except Exception as e:
                    logger.error("Error processing batch", e)
                    raise e
                finally:
                    self.__mark_queue_done(len(batch))
