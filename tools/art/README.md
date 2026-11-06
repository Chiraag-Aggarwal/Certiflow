# Academic artwork source

The app loads finished images from `src/main/resources/art/`. Files here are development inputs, not runtime dependencies.

- `study-books-prompt.txt` records the illustration brief for the book image.
- `CreateAcademicAssets.java` draws the small sparkle animation frame by frame. It does not change the original Certiflow logo.

To regenerate the sparkle GIF, run from the project root with Java 17:

```sh
java tools/art/CreateAcademicAssets.java src/main/resources/art/study-sparkle.gif
```

Review the animation before committing changes. Source-file mode avoids leaving a compiled `.class` beside the generator.
