const sharedPlugins = ['import', 'prettier', 'standard'];

const sharedExtends = [
  'standard',
  'plugin:prettier/recommended',
  'prettier',
  'prettier/standard',
];

const sharedRules = {
  'no-multi-spaces': 'off',
  'no-multi-str': 'off',
  'no-mixed-operators': 'off',
  'no-return-assign': 'off',
  'prettier/prettier': 'error',
  'no-restricted-syntax': ['error'],
};

module.exports = {
  parser: 'babel-eslint',
  plugins: sharedPlugins,
  extends: sharedExtends,
  rules: sharedRules,
  overrides: [
    {
      files: ['*.ts', '*.tsx'],
      parser: '@typescript-eslint/parser',
      plugins: [...sharedPlugins, '@typescript-eslint'],
      extends: [...sharedExtends, 'plugin:@typescript-eslint/recommended'],
      rules: {
        ...sharedRules,
        'no-use-before-define': 'off',
        '@typescript-eslint/array-type': ['error'],
        '@typescript-eslint/no-use-before-define': [
          'error',
          { functions: false },
        ],
        '@typescript-eslint/no-unused-vars': [
          'error',
          { ignoreRestSiblings: true },
        ],
      },
    },
  ],
};
