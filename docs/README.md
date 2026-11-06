# Public-link viewer

This directory is the website published by GitHub Pages from `main` / `docs`. It is separate from the JavaFX app and from the project guides in `documentation/`.

- `index.html` defines the publication and error states.
- `share.css` styles the viewer for narrow and wide browsers.
- `share.js` reads the link token from the URL fragment, requests an explicitly published preview and downloads only selected supporting files.
- `.nojekyll` keeps GitHub Pages serving these files directly.

`PROJECT_URL` and `PUBLIC_KEY` in `share.js` identify the Supabase project. The key must be publishable; never add a service-role key or a user's access token. The `get_public_share` function and storage policies in `../database/` enforce access. Hiding a field in this page is not a privacy boundary.

User-supplied text is rendered with `textContent`, and external links must pass `safeLink`. Preserve both safeguards when editing the page. A link without a publication token does not list records. To change the hosting address, also update the desktop configuration described in [database setup](../database/README.md).
