# Заглушки иконок Kotakula Multiverse

Раздел 13.2 ТЗ: пока своей иконки нет, движок показывает иконку прототипа. Таблица соответствий лежит в `jsons/IconPlaceholders.json` мода и генерируется командой `./gradlew :tests:kotakulaMod`. Заглушка действует, только пока в папке `Images` мода нет файла с таким именем: нарисованная иконка заменяет её автоматически.

Уже нарисованы (P1): иконки маны и корпоративности, 2 иконки фракций, 2 иллюстрации окна выбора, 10 новых ресурсов, 9 новых улучшений, глаз шпионажа.

Векторные иконки (P2, P3): 317 файлов SVG в `art/kotakula-icons` — все технологии, юниты, здания, чудеса, ветки и политики, корпорации, верования и действия веры, уникальные для мода. Они рендерятся в PNG командой `./gradlew :tests:kotakulaSvgIcons`. Объекты с ванильным ID, чья ванильная иконка уже совпадает с описанием в разделе 13, перечислены в `art/kotakula-icons/keep-vanilla.txt`.

Ниже — только псевдонимы: общие объекты, которые в моде названы иначе, чем в ванили, и показывают ту же самую ванильную иконку.

| Иконка | Берётся из | Почему |
| --- | --- | --- |
| `UnitIcons/Work Boat` | `UnitIcons/Work Boats` | ванильная Work Boats |
| `BuildingIcons/Great Lighthouse` | `BuildingIcons/The Great Lighthouse` | ванильное The Great Lighthouse |
| `BuildingIcons/Great Library` | `BuildingIcons/The Great Library` | ванильное The Great Library |
| `BuildingIcons/Pyramids` | `BuildingIcons/The Pyramids` | ванильное The Pyramids |
