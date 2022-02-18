import { createWriteStream, existsSync, mkdirSync } from 'fs';
import fetch from 'node-fetch';
import { dirname } from 'path';
import { SourceIdentifier, SourceWork } from './models';

// https://stackoverflow.com/a/51302466
const downloadFile = async (url: string | undefined, path: string) => {
  if (typeof url === 'undefined') {
    return;
  }

  // Ensure the directory exists.  Theoretically can throw an error if the dir
  // pops into existence before we call mkdirSync(), but unlikely.
  if (!existsSync(dirname(path))) {
    mkdirSync(dirname(path));
  }

  const res = await fetch(url);
  const fileStream = createWriteStream(path);
  await new Promise((resolve, reject) => {
    res.body.pipe(fileStream);
    res.body.on('error', reject);
    fileStream.on('finish', resolve);
  });
};

async function getImageAttributes(
  sourceIdentifier: SourceIdentifier,
  label: string,
  url: () => Promise<string>
): Promise<Record<string, string>> {
  const outPath = `_images/${sourceIdentifier.identifierType}/${sourceIdentifier.value}.jpg`;

  if (!existsSync(outPath)) {
    await downloadFile(await url(), outPath);
  }

  if (existsSync(outPath)) {
    return {
      shape: 'box',
      label: `<
        <table cellspacing="0" border="0" cellborder="0">
          <tr><td><img src="${outPath}"/></td></tr>
          <tr><td>${label.replaceAll('\n', '<br/>')}</td></tr>
        </table>
      >`,
    };
  } else {
    return { label: label };
  }
}

type Thumbnail = {
  url: string;
};

type CatalogueWork = {
  thumbnail?: Thumbnail;
};

type CatalogueResults = {
  results: CatalogueWork[];
};

async function getMetsThumbnail(bnumber: string): Promise<string | undefined> {
  const resp = await fetch(
    `https://api.wellcomecollection.org/catalogue/v2/works?identifiers=${bnumber}`
  );

  if (resp.status !== 200) {
    return undefined;
  }

  const json: CatalogueResults = await resp.json();

  return json.results.map(w => w.thumbnail?.url)?.[0];
}

function getLabelAttributes(
  w: SourceWork
): Record<string, string> | Promise<Record<string, string>> {
  switch (w.sourceIdentifier?.identifierType) {
    case 'SierraSystemNumber':
      return { label: `Sierra\n${w.sourceIdentifier.value}\n(${w.canonicalId})` };

    case 'CalmRecordIdentifier':
      return { label: `CALM\n${w.sourceIdentifier.value}\n(${w.canonicalId})` };

    case 'MiroImageNumber':
      return getImageAttributes(
        w.sourceIdentifier,
        `Miro\n${w.sourceIdentifier.value}\n(${w.canonicalId})`,
        async () =>
          `https://iiif.wellcomecollection.org/thumbs/${w.sourceIdentifier.value}/full/!100,100/0/default.jpg`
      );

    case 'METS':
      return getImageAttributes(
        w.sourceIdentifier,
        `METS\n${w.sourceIdentifier.value}\n(${w.canonicalId})`,
        async () => getMetsThumbnail(w.sourceIdentifier.value)
      );

    case undefined:
      return {
        label: `(${w.canonicalId})`,
      };

    default:
      return {
        label: `${w.sourceIdentifier.identifierType}\n${w.sourceIdentifier.value}\n(${w.canonicalId})`,
      };
  }
}

export async function getAttributes(
  w: SourceWork
): Promise<Record<string, string>> {
  const labelAttributes = await getLabelAttributes(w);

  const styleAttributes = w.suppressed ? { style: 'dashed' } : {};

  return {
    ...labelAttributes,
    ...styleAttributes,
  };
}
