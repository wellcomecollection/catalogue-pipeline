type Identifier = {
  canonicalId: string;
  sourceIdentifier: {
    identifierType: {
      id: string;
    };
    ontologyType: 'Work';
    value: string;
  };
  otherIdentifiers: unknown[];
};

export type IndexedWork = {
  type: string;
  debug: {
    source: {
      id: string;
      identifier: Identifier['sourceIdentifier']; // yes, this is awful naming
    };
    mergeCandidates: {
      id: Identifier;
      reason: string;
    }[];
  };
};
