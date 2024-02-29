import fetch from 'node-fetch';
import { dirname } from 'path';
import { copyFile, createWriteStream, existsSync, mkdirSync } from 'fs';
import { IndexedWork } from './models';
import { NodeAttributes } from 'ts-graphviz';

type Thumbnail = {
  url: string;
};

type CatalogueWork = {
  thumbnail?: Thumbnail;
};

type CatalogueResults = {
  results: CatalogueWork[];
};

// https://stackoverflow.com/a/51302466
const downloadFile = async (url: string | undefined, path: string) => {
  if (typeof url === 'undefined') {
    return;
  }

  // Ensure the directory exists.  Theoretically can throw an error if the dir
  // pops into existence before we call mkdirSync(), but unlikely.
  if (!existsSync(dirname(path))) {
    mkdirSync(dirname(path), { recursive: true });
  }

  const res = await fetch(url);
  const fileStream = createWriteStream(path);
  await new Promise((resolve, reject) => {
    res.body.pipe(fileStream);
    res.body.on('error', reject);
    fileStream.on('finish', resolve);
  }).then(async _ => {
    if (!res.ok) {
      console.log('not ok');
      console.log(path);
      console.log(url);
      copyFile('404.jpg', path, err => {
        console.log('Error Found:', err);
      });
    }
  });
};

const getMetsThumbnail = async (
  bnumber: string
): Promise<string | undefined> => {
  const resp = await fetch(
    `https://api.wellcomecollection.org/catalogue/v2/works?identifiers=${bnumber}`
  );

  if (resp.status !== 200) {
    return undefined;
  }

  const json: CatalogueResults = await resp.json();

  return json.results.map(w => w.thumbnail?.url)?.[0];
};

const getSourceLabel = (sourceTypeId: string): string => {
  switch (sourceTypeId) {
    case 'sierra-system-number':
      return 'Sierra';
    case 'calm-record-id':
      return 'CALM';
    case 'mets':
    case 'mets-image':
      return 'METS';
    case 'miro-image-number':
      return 'Miro';
    default:
      return sourceTypeId;
  }
};

const getThumbnailSrc = async (w: IndexedWork): Promise<string | undefined> => {
  const sourceIdValue = w.debug.source.identifier.value;
  const sourceTypeId = w.debug.source.identifier.identifierType.id;
  switch (sourceTypeId) {
    case 'mets':
    case 'mets-image':
      return getMetsThumbnail(sourceIdValue);
    case 'miro-image-number':
      return `https://iiif.wellcomecollection.org/thumbs/${sourceIdValue}/full/!100,100/0/default.jpg`;
    default:
      return undefined;
  }
};

const getLabel = (w: IndexedWork): string => {
  const canonicalId = w.debug.source.id;
  const sourceIdValue = w.debug.source.identifier.value;
  const sourceTypeId = w.debug.source.identifier.identifierType.id;

  return [
    getSourceLabel(sourceTypeId),
    sourceIdValue,
    `(${canonicalId})`,
    w.type === 'Deleted' ? '(suppressed)' : undefined,
  ]
    .filter(Boolean)
    .join('\n');
};

export async function getAttributes(w: IndexedWork): Promise<NodeAttributes> {
  const textLabel = getLabel(w);
  const thumbnailSrc = await getThumbnailSrc(w);

  let outPath = '';
  if (thumbnailSrc) {
    outPath = `_images/${w.debug.source.identifier.identifierType.id}/${w.debug.source.identifier.value}.jpg`;

    if (!existsSync(outPath)) {
      await downloadFile(thumbnailSrc, outPath);
    }
  }

  const label = thumbnailSrc
    ? `<
        <table cellspacing="0" border="0" cellborder="0">
          <tr><td><img src="${outPath}"/></td></tr>
          <tr><td>${textLabel.replaceAll('\n', '<br/>')}</td></tr>
          <tr><td></td></tr>
        </table>
      >`
    : textLabel;
  return {
    label,
    shape: thumbnailSrc ? 'box' : undefined,
    style: w.type === 'Deleted' ? 'dashed' : undefined,
  };
}
