export type SourceIdentifier = {
  identifierType: string;
  value: string;
}
  
export type SourceWork = {
  canonicalId: string;
  mergeCandidateIds: string[];
  componentIds: string[];
  suppressed: boolean;
  sourceIdentifier: SourceIdentifier;
}
