# KnuaHome

Multi-version **Paper & Folia** (`1.21` – `26.3`) Home plugin with native **NMS Dialog UI** (`1.21.6+`) and automatic **Fallback Inventory GUI** (`< 1.21.6`).  
Plugin Home đa phiên bản cho **Paper & Folia** (`1.21` – `26.3`), hỗ trợ **NMS Dialog UI** (`1.21.6+`) và tự động chuyển **Fallback Inventory GUI** cho `< 1.21.6`.

[English](#english) | [Tiếng Việt](#tiếng-việt)

---

## English

### Overview
- **NMS Core (`1.21` – `26.3`)**: Uses Minecraft's native Dialog UI (`1.21.6+`) with smooth multi-stage `Show More` expansion and white bed `PlayerSprite` icons (`1.21.9+`).
- **Automatic Fallback GUI**: Servers on `1.21` – `1.21.5`, or legacy clients connecting via **ViaVersion**, automatically receive the DonutSMP-style Chest/Hopper GUI (with Teleport, Chat Rename, Icon Picker across A–Z items, and NMS Virtual Sign Search).
- **Thread-Safe & High Throughput**: Native **Folia** entity scheduler support (`folia-supported: true`), batched SQLite WAL persistence (`plugins/KnuaHome/homes.db`), lock-free memory cache reads, and Netty click cooldown protection.

### Installation
1. **Requirements**:
   - **Server**: Paper or Folia `1.21` – `26.3` (`api-version: '1.21'`).
   - **Java**: Java `21+` for `1.21` – `1.21.11`, or Java `25` for `26.1` – `26.3`.
   - **Optional (`softdepend`)**: `LuckPerms`, `PlaceholderAPI`, `ViaVersion`.
2. Download or build `KnuaHome-1.0.1.jar` and place it in your server's `plugins/` folder.
3. Start or restart the server to generate `plugins/KnuaHome/config.yml`, `messages.yml`, and `homes.db`.
4. Edit `config.yml` (ranks, `max-homes`, `default-homes`) and `messages.yml` (MiniMessage / `&` legacy text and sounds), then apply changes with `/knuahome reload`.

### Commands, Permissions & Placeholders

| Command | Aliases | Description | Permission | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/home [slot]` | `/homes` | Open Homes UI or teleport to a home slot | `knuahome.use` | `true` |
| `/sethome [slot]` | — | Set a home at your current location | `knuahome.use` | `true` |
| `/delhome [slot]` | — | Delete an existing home slot | `knuahome.use` | `true` |
| `/knuahome reload` | — | Reload `config.yml` and `messages.yml` | `knuahome.admin` | `op` |

- **Home Limit Resolution** *(works with or without LuckPerms; highest resolved limit wins, capped at `max-homes: 100`)*:
  - Numeric nodes: `knuahome.homes.<number>` or `knuahome.limit.<number>`
  - Rank nodes: `knuahome.rank.<rank>` or `group.<rank>` *(built-in `config.yml` defaults: `default: 2`, `vip: 5`, `mvp: 10`)*
  - LuckPerms *(optional)*: Automatically checks the player's primary group and inherited groups.
- **PlaceholderAPI**:
  - `%knuahome_count%` — Number of homes currently set by the player.
  - `%knuahome_max%` — Maximum allowed homes for the player.

### Build from Source
1. **Install JDKs**:
   - **JDK 21** to run Gradle (`JAVA_HOME` pointing to your JDK 21 installation).
   - **JDK 25** for Gradle Toolchain *(automatically discovered by Gradle, or configure `org.gradle.java.installations.paths` in your `~/.gradle/gradle.properties`)*. The build compiles against Paper `26.3` while emitting Java 21 bytecode (`--release 21`).
2. **Provide Paper Server Jars** *(required for NMS bindings)*:
   - Download Paper server jars for **`1.21`**, **`1.21.6`**, **`1.21.9`**, **`1.21.11`**, **`26.2`**, and **`26.3`** from [papermc.io/downloads/paper](https://papermc.io/downloads/paper).
   - Place them inside a `paper-jars/` folder in the repository root (e.g. `paper-jars/paper-1.21.11-132.jar`), or pass `-PpaperArchiveDir=/path/to/paper-jars` / set `PAPER_ARCHIVE_DIR`.
   - *(LuckPerms, PlaceholderAPI, and ViaVersion APIs are resolved automatically from Maven.)*
3. **Compile**:

```bash
# Linux / macOS
./gradlew clean shadowJar --no-daemon
```

```powershell
# Windows (PowerShell)
.\gradlew.bat clean shadowJar --no-daemon
```

- Output JAR: `build/libs/KnuaHome-1.0.1.jar` *(also built by `./gradlew clean build`)*

### License
Licensed under the **KnuaHome Educational & Reference License** ([LICENSE](./LICENSE)):
- Allowed for studying, referencing, and personal learning.
- **Strictly prohibited** to rename/rebrand the project or sell/resell it (original or modified) as another product.

---

## Tiếng Việt

### Tổng Quan
- **NMS Core (`1.21` – `26.3`)**: Hiển thị trực tiếp màn hình **Dialog UI** của Minecraft (`1.21.6+`) hỗ trợ mở rộng `Show More` mượt mà và icon giường `PlayerSprite` (`1.21.9+`).
- **Tự Động Chuyển Fallback GUI**: Với server `< 1.21.6` (`1.21` – `1.21.5`) hoặc người chơi dùng bản cũ qua **ViaVersion**, plugin tự động mở GUI rương/phễu kiểu DonutSMP (Dịch chuyển, Đổi tên qua chat, Chọn icon A–Z và Tìm kiếm bằng Sign ảo NMS).
- **Tương Thích Folia & Hiệu Năng Cao**: Dùng scheduler theo từng player (`folia-supported: true`), lưu trữ SQLite WAL gộp transaction (`plugins/KnuaHome/homes.db`), đọc cache RAM lock-free và chống spam gói tin click ở tầng Netty.

### Cài Đặt
1. **Yêu cầu hệ thống**:
   - **Server**: Paper hoặc Folia `1.21` – `26.3` (`api-version: '1.21'`).
   - **Java**: Java `21+` cho `1.21` – `1.21.11`, hoặc Java `25` cho `26.1` – `26.3`.
   - **Plugin hỗ trợ thêm (`softdepend`, không bắt buộc)**: `LuckPerms`, `PlaceholderAPI`, `ViaVersion`.
2. Tải hoặc tự build file `KnuaHome-1.0.1.jar` và chép vào thư mục `plugins/` của server.
3. Khởi động / khởi động lại server để tạo `plugins/KnuaHome/config.yml`, `messages.yml` và `homes.db`.
4. Chỉnh sửa giới hạn rank trong `config.yml` hoặc thông báo/âm thanh (MiniMessage & mã màu `&`) trong `messages.yml`, rồi chạy `/knuahome reload`.

### Lệnh, Quyền & Placeholders

| Lệnh | Tên phụ | Mô tả | Quyền | Mặc định |
| :--- | :--- | :--- | :--- | :--- |
| `/home [slot]` | `/homes` | Mở giao diện Homes hoặc dịch chuyển tới slot | `knuahome.use` | `true` |
| `/sethome [slot]` | — | Lưu vị trí hiện tại vào slot home | `knuahome.use` | `true` |
| `/delhome [slot]` | — | Xóa một slot home đã lưu | `knuahome.use` | `true` |
| `/knuahome reload` | — | Tải lại cấu hình `config.yml` và `messages.yml` | `knuahome.admin` | `op` |

- **Cách tính giới hạn Home** *(chạy độc lập không cần LuckPerms; tự chọn mức cao nhất, tối đa theo `max-homes: 100`)*:
  - Quyền số lượng trực tiếp: `knuahome.homes.<số>` hoặc `knuahome.limit.<số>`
  - Quyền theo rank: `knuahome.rank.<rank>` hoặc `group.<rank>` *(mặc định trong `config.yml`: `default: 2`, `vip: 5`, `mvp: 10`)*
  - LuckPerms *(tùy chọn)*: Tự động quét thêm group chính và các group kế thừa.
- **PlaceholderAPI**:
  - `%knuahome_count%` — Số home người chơi đã đặt.
  - `%knuahome_max%` — Số home tối đa người chơi được phép đặt.

### Build từ Source
1. **Cài đặt JDK**:
   - **JDK 21** để chạy Gradle (trỏ `JAVA_HOME` về thư mục JDK 21 trên máy của bạn).
   - **JDK 25** cho Gradle Toolchain *(Gradle tự nhận diện trên máy, hoặc bạn có thể khai báo `org.gradle.java.installations.paths` trong `~/.gradle/gradle.properties`)*. Quá trình build biên dịch với NMS `26.3` nhưng xuất bytecode Java 21 (`--release 21`).
2. **Chuẩn bị file Paper Server Jar** *(cần cho các module NMS)*:
   - Tải 6 bản Paper server jar cho **`1.21`**, **`1.21.6`**, **`1.21.9`**, **`1.21.11`**, **`26.2`** và **`26.3`** từ [papermc.io/downloads/paper](https://papermc.io/downloads/paper).
   - Đặt vào thư mục `paper-jars/` ở gốc repo (ví dụ `paper-jars/paper-1.21.11-132.jar`), hoặc truyền `-PpaperArchiveDir=/duong/dan/toi/jars` / biến môi trường `PAPER_ARCHIVE_DIR`.
   - *(Các API LuckPerms, PlaceholderAPI, ViaVersion được tải tự động qua Maven.)*
3. **Biên dịch**:

```bash
# Linux / macOS
./gradlew clean shadowJar --no-daemon
```

```powershell
# Windows (PowerShell)
.\gradlew.bat clean shadowJar --no-daemon
```

- File JAR xuất ra tại: `build/libs/KnuaHome-1.0.1.jar` *(hoặc chạy `./gradlew clean build`)*

### Giấy Phép
Dự án sử dụng **KnuaHome Educational & Reference License** ([LICENSE](./LICENSE)):
- Cho phép xem mã nguồn để tham khảo, học hỏi và sử dụng cá nhân.
- **Nghiêm cấm** sửa tên, xóa thông tin tác giả hoặc mang bán lại (bản gốc hay bản chỉnh sửa) dưới dạng một sản phẩm khác.
