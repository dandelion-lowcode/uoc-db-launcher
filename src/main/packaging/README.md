# packaging

Inputs for `jpackage`, not resources the application reads. They live outside
`src/main/resources` so that they do not travel inside the jar, where nothing would ever
open them.

Both are rendered from `src/main/resources/icons/icon.svg`, which stays the one place the
application's icon is drawn. **Change the SVG and these have to be made again**, or the
window will show one icon and the Dock or taskbar another.

- `icon.png` -- 1024x1024, used as it is on Linux.
- `icon.ico` -- what Windows requires, holding 16 through 256 pixel versions.
- `icon.icns` -- what macOS requires, holding eleven versions from 16 to 1024 pixels,
  including the doubled densities a modern display asks for.

Every size in all three is rendered from the vector at that size, rather than scaled down
from one large bitmap, so the sixteen-pixel version is as sharp as the rest.

The three overlap heavily, which is on purpose: the alternative was building the macOS
one on the runner, and a packaging step that can fail is a worse trade than a few hundred
kilobytes that never change.

## Making them again

Both containers are simple, and neither needs a library:

- **`.ico`** -- a six-byte header, a sixteen-byte directory entry per size, then the PNG
  bytes themselves, which Windows has accepted inside an `.ico` since Vista.
- **`.icns`** -- `icns`, the total length, then one chunk per image: a four-character type
  (`icp4`, `ic07`, `ic10` and so on, each meaning a particular size and density), the
  chunk length including its own eight-byte header, and again the PNG bytes.

Render each size with FlatLaf's `FlatSVGIcon` onto a `BufferedImage`, write it with
`ImageIO`, and pack the results.

Why this is not part of the build: it would add an image-generation step to every compile
for three files that change perhaps once in the life of the project.
