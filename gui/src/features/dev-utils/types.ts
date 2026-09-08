export interface DevUtilsResponse {
  output: string;
}

// Mirrors dev-utils-service's own dto.StringCaseResponse field-for-field — the one operation in
// this feature whose output is genuinely richer than a single string, so it gets its own type
// rather than being forced into DevUtilsResponse (see that backend record's own Javadoc).
export interface StringCaseResponse {
  camelCase: string;
  pascalCase: string;
  snakeCase: string;
  kebabCase: string;
  constantCase: string;
  titleCase: string;
  sentenceCase: string;
}
