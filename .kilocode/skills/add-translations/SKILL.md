---
name: add-translations
description: Add missing translations to i18n files in the codebase
---

When you are working with i18n strings in this project, utilize the following steps to identify missing translations and add them to the language files.

First, to identify missing translation keys: `bun run --cwd packages/kilo-vscode i18n:missing`. This command will print a list of translation keys, with their English string, that are present in the English i18n file but are missing from other translation files.

If the command prints "No missing translations found", stop here. There is no work that needs to be done.

If the command prints a list of files and translation keys, do the following steps for each file in the command's output:

- For each translation key noted, utilize the Translator sub-agent to translate the English string to the target language
- Insert the key with the translated string into the target i18n TypeScript file

When finished with this task for all files, rerun the `bun run --cwd packages/kilo-vscode i18n:missing` command to validate completion.
