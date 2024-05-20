import { existsSync, mkdirSync, writeFileSync } from 'fs';
import fetch from 'node-fetch';
import { Digraph, digraph, RootCluster, toDot } from 'ts-graphviz';
import { execSync } from 'child_process';
import { IndexedWork } from './models';
import { getAttributes } from './graphAttributes';
import { getPipelineClient } from './elasticsearch';

type UserInput = {
  canonicalId: string;
  pipelineDate: string;
};

type ElasticConfig = {
  worksIndex: string;
  imagesIndex: string;
};

type ElasticsearchClient = Awaited<ReturnType<typeof getPipelineClient>>;

async function getInput(): Promise<UserInput> {
  let id: string;
  let specifiedPipelineDate: string | undefined;

  if (process.argv.length === 4) {
    specifiedPipelineDate = process.argv[3];
    id = process.argv[2];
  } else if (process.argv.length === 3) {
    id = process.argv[2];
  } else {
    console.error('Usage: getGraph <WORK_ID> [<PIPELINE_DATE>]');
    process.exit(1);
  }

  const resp = await fetch(
    'https://api.wellcomecollection.org/catalogue/v2/_elasticConfig'
  );

  if (resp.status !== 200) {
    throw Error('Could not fetch ElasticConfig from API');
  }

  const elasticConfig: ElasticConfig = await resp.json();
  const pipelineDate =
    specifiedPipelineDate ??
    elasticConfig.worksIndex.replace('works-indexed-', '');

  return {
    canonicalId: id,
    pipelineDate: pipelineDate,
  };
}

async function getWorksGraph(
  client: ElasticsearchClient,
  input: UserInput
): Promise<RootCluster> {
  const g = digraph(
    'G',

    // This tells Graphviz to lay out the nodes left-to-right, because we tend
    // to have monitors that are wider than tall, so it's easier to lay things
    // out this way.
    // See https://graphviz.org/docs/attrs/rankdir/
    { rankdir: 'LR' }
  );
  const worksIndex = `works-indexed-${input.pipelineDate}`;

  const buildGraph = async (workId: string): Promise<Digraph> => {
    const work = await client.get<IndexedWork>({
      index: worksIndex,
      id: workId,
    });
    if (!work.found) {
      throw new Error(`Work ${workId} not found in index!`);
    }
    const mergeCandidates = work._source.debug.mergeCandidates;

    const inNodes = await client.search<IndexedWork>({
      index: worksIndex,
      query: {
        term: {
          'debug.mergeCandidates.id.canonicalId': workId,
        },
      },
    });

    g.createNode(workId, await getAttributes(work._source));
    mergeCandidates.forEach(mergeCandidate => {
      g.createEdge([workId, mergeCandidate.id.canonicalId], {
        label: mergeCandidate.reason,
      });
    });
    await Promise.all(
      inNodes.hits.hits
        .filter(hit => !g.existNode(hit._id))
        .map(async hit => {
          g.createNode(hit._id, await getAttributes(hit._source));
          g.createEdge([hit._id, workId], {
            label: hit._source.debug.mergeCandidates.find(
              m => m.id.canonicalId === workId
            )?.reason,
          });
        })
    );

    const newNodes = mergeCandidates
      .map(m => m.id.canonicalId)
      .filter(id => !g.existNode(id));
    if (newNodes.length !== 0) {
      await Promise.all(newNodes.map(buildGraph));
    }
    return g;
  };

  return buildGraph(input.canonicalId);
}

// Takes the in-memory graph, renders it as a PDF and returns the filename.
function createPdf(canonicalId: string, g: RootCluster): string {
  if (!existsSync('_graphs')) {
    mkdirSync('_graphs');
  }

  writeFileSync(`_graphs/${canonicalId}.dot`, toDot(g));
  try {
    execSync(
      `dot -Tpdf _graphs/${canonicalId}.dot -o _graphs/${canonicalId}.pdf`
    );
  } catch (error) {
    if (error.message.includes('not found')) {
      console.log(
        'dot, not found try installing it with `brew install graphviz`'
      );
      process.exit(1);
    } else {
      throw error;
    }
  }
  return `_graphs/${canonicalId}.pdf`;
}

export default async function getGraph(): Promise<void> {
  const input = await getInput();

  const elasticsearchClient = await getPipelineClient(input.pipelineDate);

  const g = await getWorksGraph(elasticsearchClient, input);

  const filename = createPdf(input.canonicalId, g);

  console.log(`Written ${filename}`);

  execSync(`open ${filename}`);
}

getGraph();
