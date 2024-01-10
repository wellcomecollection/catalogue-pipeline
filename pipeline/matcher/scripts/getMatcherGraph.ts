import { existsSync, mkdirSync, writeFileSync } from 'fs';
import fetch from 'node-fetch';
import { DynamoDBClient } from '@aws-sdk/client-dynamodb';
import { DynamoDBDocument, QueryCommandInput } from '@aws-sdk/lib-dynamodb';
import { getCreds } from '@weco/ts-aws/sts';
import { digraph, INode, RootCluster, toDot } from 'ts-graphviz';
import { execSync } from 'child_process';
import { SourceWork } from './models';
import { getAttributes } from './graphAttributes';

type UserInput = {
  canonicalId: string;
  pipelineDate: string;
};

type ElasticConfig = {
  worksIndex: string;
  imagesIndex: string;
};

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

async function queryAllMatchingItems(
  client: DynamoDBDocument,
  query: QueryCommandInput
): Promise<Record<string, any>[]> {
  const result = await client.query(query);

  const firstPageOfItems = result.Items ? result.Items : [];

  const remainingItems = result.LastEvaluatedKey
    ? await queryAllMatchingItems(client, {
        ...query,
        ExclusiveStartKey: result.LastEvaluatedKey,
      })
    : [];

  return [...firstPageOfItems, ...remainingItems];
}

async function getRelevantWorks(
  client: DynamoDBDocument,
  input: UserInput
): Promise<SourceWork[]> {
  const tableName = `catalogue-${input.pipelineDate}_works-graph`;

  console.log(`Querying ${tableName} for ${input.canonicalId}`);

  const output = await client.get({
    TableName: tableName,
    Key: { id: input.canonicalId },
  });

  if (typeof output.Item === 'undefined') {
    throw new Error(
      `Could not find a matched work with ID ${input.canonicalId}`
    );
  }

  const subgraphId = output.Item!.subgraphId;

  const query: QueryCommandInput = {
    TableName: tableName,
    IndexName: 'work-sets-index',
    KeyConditionExpression: '#subgraphId = :subgraphId',
    ExpressionAttributeNames: { '#subgraphId': 'subgraphId' },
    ExpressionAttributeValues: { ':subgraphId': subgraphId },
  };

  const worksInSubgraph: Record<string, any>[] = await queryAllMatchingItems(
    client,
    query
  );

  return worksInSubgraph.map((item: Record<string, any>) => {
    return {
      canonicalId: item.id,
      mergeCandidateIds: item.sourceWork?.mergeCandidateIds,
      suppressed: item.sourceWork?.suppressed,
      componentIds: item.componentIds,
      sourceIdentifier: item.sourceWork
        ? {
            value: item.sourceWork.id.value,

            // This is an artefact of a slightly weird format in DynamoDB,
            // where the identifierType is recorded as e.g.
            //
            //    {"identifierType": {"METS": "METS"}, ...}
            //
            // We might simplify this at some point.
            identifierType: Object.keys(item.sourceWork.id.identifierType)[0],
          }
        : undefined,
    };
  });
}

async function createGraph(works: SourceWork[]): Promise<RootCluster> {
  const g = digraph(
    'G',

    // This tells Graphviz to lay out the nodes left-to-right, because we tend
    // to have monitors that are wider than tall, so it's easier to lay things
    // out this way.
    // See https://graphviz.org/docs/attrs/rankdir/
    { rankdir: 'LR' }
  );

  // Create all the nodes
  const nodes: Map<string, INode> = new Map();
  await Promise.all(
    works.map(async (w: SourceWork) => {
      const attributes = await getAttributes(w);

      const newNode = g.createNode(w.canonicalId, attributes);

      nodes.set(w.canonicalId, newNode);
    })
  );

  // Add all the edges
  works.forEach((w: SourceWork) => {
    (w.mergeCandidateIds ?? []).forEach((target: string) => {
      // Make sure this node is in the graph.  If it's not in the graph, it means
      // this Work has a merge candidate that points to a Work the matcher hasn't
      // seen.  Show it on the graph, but make it clear we don't know what it is.
      if (!nodes.has(target)) {
        nodes.set(target, g.createNode(target, { style: 'dotted' }));
      }

      // If A->B but they're in different components, it means one or both of them
      // is suppressed.  Include the edge, but make it clear it's not used.
      const attributes = {};
      if (!w.componentIds.includes(target)) {
        attributes['style'] = 'dotted';
      }

      g.createEdge([nodes.get(w.canonicalId), nodes.get(target)], attributes);
    });
  });

  return g;
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

  const credentials = await getCreds('platform', 'read_only');
  const dynamoDbClient = new DynamoDBClient({
    credentials,
    region: 'eu-west-1',
  });
  const documentClient = DynamoDBDocument.from(dynamoDbClient);

  const works = await getRelevantWorks(documentClient, input);

  const g = await createGraph(works);

  const filename = createPdf(input.canonicalId, g);

  console.log(`Written ${filename}`);

  execSync(`open ${filename}`);
}

getGraph();
