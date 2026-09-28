# Embedded PDF fonts

`NotoSans-Regular.ttf` and `NotoSans-Bold.ttf` are unmodified static TTFs
from the official [notofonts/NotoSans](https://github.com/notofonts/NotoSans)
repository, path `fonts/ttf/hinted/instance_ttf/`, retrieved from its `main`
branch. They are embedded and subset by `PdfDocumentService` so PDFs do not
depend on fonts installed in a runtime container or on a developer workstation.

They are licensed under the SIL Open Font License 1.1. The complete notice is
included unchanged in `NotoSans-OFL.txt`.
