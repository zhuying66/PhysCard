# FSRS 选型评估

评估日期：2026-10-17

## 结论

项目没有直接使用 `open-spaced-repetition/FSRS-Kotlin`，而是使用同一个官方组织维护的 **java-fsrs**：

```kotlin
implementation("io.github.open-spaced-repetition:fsrs:1.0.0")
```

原因不是偏好 Java，而是 FSRS-Kotlin 在评估时不是一个可复用、可发布的 Android 库；java-fsrs 是官方组织提供的、已发布到 Maven Central 的纯 JVM 实现，API 和许可证都明确，且与当前 `minSdk 26` + Java 17 desugaring 的工程兼容。

本项目的 `FsrsSchedulingService` 只做适配，不包含任何自写的 FSRS 调度公式。

## 候选一：FSRS-Kotlin

链接：<https://github.com/open-spaced-repetition/FSRS-Kotlin>

检查到的仓库内容：

- 根目录只有 `FSRS.kt`、`models.kt`、`README.md`；
- 没有 `build.gradle(.kts)`、`settings.gradle`、Gradle Wrapper 或测试；
- 没有 Maven Central / JitPack 坐标；
- `FSRS.kt` 依赖 `app.arteh.flashcard.db.FlashCard`、`Grade`、`Rating` 和 `R.color.*`，是从某个示例 App 中提取的代码；
- `README` 只说明“用 Android Studio 打开”，没有库 API 文档；
- 没有 LICENSE 文件。

此外，代码里的新卡/学习步骤逻辑需要示例应用的 `FlashCard` 字段，直接复制会引入一套与当前项目不匹配的数据模型；而重写这部分又违背“不要自行重写 FSRS 核心数学算法”的要求。

**结论：不采用为依赖。** 该仓库可以作为交互参考，但不能作为生产依赖或可靠的算法实现来源。

## 候选二：java-fsrs（采用）

链接：<https://github.com/open-spaced-repetition/java-fsrs>

| 项目 | 结论 |
| --- | --- |
| 组织 | `open-spaced-repetition` 官方组织 |
| 许可证 | MIT |
| 发布 | Maven Central `io.github.open-spaced-repetition:fsrs`，当前版本 `1.0.0` |
| 语言/运行时 | Java 17，纯 JVM |
| 测试 | 仓库包含 `FSRSTest.java`，覆盖初始化、学习/复习/重新学习、模糊、序列化等 |
| 参数 | 21 个 FSRS-5 权重，支持 `desiredRetention`、`learningSteps`、`relearningSteps`、`maximumInterval`、`enableFuzzing` |
| 时间 | 全部使用 UTC `Instant`，符合调度时间的持久化要求 |
| 依赖 | Jackson（仅用于官方库自带的可选 JSON API）；本工程显式固定了 Jackson 版本并保留 R8 规则 |

核心 API（实际使用）：

```java
Scheduler scheduler = Scheduler.builder()
    .desiredRetention(0.90)
    .learningSteps(new Duration[]{Duration.ofMinutes(10)})
    .relearningSteps(new Duration[]{Duration.ofMinutes(10)})
    .maximumInterval(36500)
    .enableFuzzing(true)
    .build();

CardAndReviewLog result = scheduler.reviewCard(card, Rating.GOOD, instant, durationMillis);
Card next = result.card();
```

## 工程集成方式

`FsrsSchedulingService` 位于 `domain/fsrs/`：

- **每个公式的每种能力一张独立 Card**：`cardKey = "<formulaId>::<ABILITY>"`；
- 将 Room 的复习状态映射为 `Card`（state、step、stability、difficulty、due、lastReview）；
- 只通过官方 `Scheduler.reviewCard` 更新状态；
- 目标保持率改变时重建 Scheduler，仅影响之后安排的复习；
- 预览四档间隔时使用独立的、关闭 fuzzing 的 Scheduler，避免预览消耗真实调度的随机序列；
- 调度器内部有随机状态，因此服务用 `Mutex` 串行化调用。

### 兼容性验证

- JVM 单元测试验证了官方调度器在 Good/Again/重新学习下的行为；
- Robolectric 测试在 Android SQLite/Room 环境中运行完整“读取状态 → 调用官方调度 → 写回状态”链路；
- R8 release 构建通过；对 java-fsrs 字节码引用的 Lombok 编译期注解使用了精确的 `-dontwarn`，生产运行时不需要 Lombok；
- `minSdk 26`，启用 core library desugaring，使用 `java.time` 与 `java.time.Duration`；
- 没有使用 java-fsrs 的 Jackson JSON 序列化 API，只将其保留在类路径中，避免 Android 上额外的反射风险；核心调度不依赖 Jackson。

### 为什么不在 Android 上使用 fsrs-rs

`fsrs-rs` 是 Rust 实现，Android 需要额外的 NDK/JNI 构建、ABI 打包与升级维护；对个人开发者的离线 App 来说，纯 JVM 的 java-fsrs 更简单、更可靠，且已经满足相同的 FSRS-5 语义。

## 许可证文本

java-fsrs 采用 MIT License。版权与许可声明见其仓库 `LICENSE` 文件；本项目在 `NOTICE.md` 中一并列出。
