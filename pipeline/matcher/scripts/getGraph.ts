import fetch from 'node-fetch';

type UserInput = {
  canonicalId: string;
  pipelineDate: string;
}

type ElasticConfig = {
  worksIndex: string;
  imagesIndex: string;
}

async function getInput(): Promise<UserInput> {
  let id: string;
  if (process.argv.length != 3) {
    console.error('Usage: getGraph <WORK_ID>');
    process.exit(1);
  } else {
    id = process.argv[2];
  }

  const resp = await fetch('https://api.wellcomecollection.org/catalogue/v2/_elasticConfig').then();

  if (resp.status !== 200) {
    throw Error('Could not fetch ElasticConfig from API');
  }

  const elasticConfig: ElasticConfig = await resp.json();
  const pipelineDate = elasticConfig.worksIndex.replace("works-indexed-", "");

  return {
    canonicalId: id,
    pipelineDate: pipelineDate,
  };
}

export default async function getGraph(): Promise<void> {
  const input = await getInput();
  console.log(input);
}

getGraph()
