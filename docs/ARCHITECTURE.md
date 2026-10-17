# 技术架构与数据设计

## 总览

```text
Compose UI
  ├── HomeScreen / LibraryScreen / FormulaDetailScreen
  ├── StudyScreen（学习、反馈、评价、总结）
  ├── StatsScreen
  └── SettingsScreen
        │ StateFlow / 事件回调
        ▼
ViewModels（Home / Library / Detail / Study / Stats / Settings / App）
        │
        ├── StudyRepository ── FsrsSchedulingService ── java-fsrs 1.0.0
        │        │                     └── MemoryLevelPolicy（独立等级层）
        │        └── Room（状态 + 日志）
        ├── ContentRepository ── SeedLoader ── assets JSON
        └── SettingsRepository ── DataStore Preferences
```

## 数据模型

### 内容

| 表 | 职责 |
| --- | --- |
| `formulas` | 公式标题、模块、章节、LaTeX、条件、变量 JSON、易错点 JSON、关联公式 |
| `questions` | 题干、刺激材料（可选公式）、所属公式、能力类型 |
| `question_options` | 选项顺序、LaTeX 或文本；每题固定 4 项 |
| `question_answers` | 正确答案下标与解析（与题目分表） |

`Question` 领域模型在构造时强制校验“四个选项 + 正确下标合法”。导入时再次做完整校验。

### 学习状态

| 表 | 职责 |
| --- | --- |
| `review_states` | 每种公式 × 能力一张 FSRS 卡：state/step/stability/difficulty/due/lastReview、复习次数、遗忘次数、正确次数、八级等级与积分 |
| `review_logs` | 每次评价的审计记录：时间、本地日期、评价、对错、选项、间隔、等级变化、耗时、是否首次学习 |

`cardKey = "<formulaId>::<ABILITY>"` 是能力状态的稳定主键。一个题型的表现不会影响另一个题型的 FSRS 字段，也不会覆盖其等级。

## FSRS 适配层

`FsrsSchedulingService`：

- 构造 `Scheduler`，固定 `desiredRetention`、学习/重新学习步骤、最大间隔、fuzzing；
- `newCardSnapshot` 建立 `LEARNING` 快照；
- `review` 调用官方 `reviewCard`，返回新快照与间隔；
- `previewIntervals` 使用独立的、关闭 fuzzing 的 Scheduler，避免预览改变真实随机序列；
- 用 `Mutex` 串行化有状态 Scheduler；
- 所有传入时间均为 UTC `Instant`，不把本地日期交给算法。

`StudyRepository.recordReview` 在一个 Room 事务中：

1. 重新读取当前状态（避免 UI 快照过期）；
2. 调用官方调度；
3. 计算 `MemoryLevelPolicy` 的等级变化；
4. 更新 `review_states`；
5. 插入 `review_logs`。

## 会话构建

`buildDailySession` 组合：

1. 已引入且到期的能力卡（最多 20 张）；
2. 今日新公式额度内的新公式，每个公式排五类能力题；
3. 最近一个未完成公式的剩余能力题（应用中断后恢复）；
4. 总上限 40 题。

新公式的公式卡只出现一次，由第一题的 `showFormulaIntro=true` 标记；之后直接进入题目。评分后立即写入数据库；重启后从来没有扫描内存中的会话，而是根据数据库状态重建，因此重启和进程死亡不会丢进度。

## 公式渲染

`FormulaParser` 支持一个有意的 TeX 子集：

- `\frac`、`\sqrt`、`\boxed`、`\text`、`\vec`、`\overline`；
- 上下标、希腊字母、`\sin/\cos/\tan`、常用运算符；
- 分式用真实分隔线，根号用矢量绘制（不是字符截图），上下标使用 `BaselineShift`。

题库中所有公式和选项 LaTeX 都在 JVM 测试中实际解析，避免上线后遇到不支持的命令。

## 时间与日期

- 持久化：UTC epoch millis / `Instant`；Room 不存储本地时区偏移。
- 统计：当前时区的 `LocalDate` 从 `reviewedAt` 重新计算；`localDate` 仍写入日志作为索引/审计，但聚合不依赖它。
- 首页/统计每分钟重新计算一次，跨过午夜或到了新的到期时间后 UI 会更新。
- FSRS 不感知本地日期，只接收绝对时间。

## 数据库升级

`AppDatabase` 当前版本 2：

- v1 → v2 增加 `(introduced, dueAt)` 和 `(formulaId, dueAt)` 的查询索引；
- 迁移 SQL 位于 `AppDatabase.MIGRATION_1_2`；
- 没有启用 `fallbackToDestructiveMigration`。缺少迁移时宁可显示启动错误，也不静默清空学习历史；
- `app/schemas` 导出 v1 与 v2 schema，供后续迁移测试/审查。

## 导入导出

- 导出：Room 内容 → `ExportBundleDto` → JSON；
- 导入：JSON → schema/字段/选项/答案/引用/重复校验 → Room 事务；
- 重复公式按 ID 与“标题 + 标准形式”双重判断；重复题目按 ID 与“(formulaId, ability, prompt)”判断；
- 题目、选项、答案分表写入；导入后补齐对应能力状态；
- 失败抛出带中文原因的错误，由设置页展示。

## 测试策略

- 纯 JVM：公式解析器、八级策略、FSRS 适配、时钟边界；
- Robolectric：真实 Room + assets + 官方 FSRS 的完整数据链路；
- Compose UI Test + Robolectric：多屏幕渲染、截图、启动 → 学习 → 评分 → 下一题的端到端流程；
- Release：R8 + resource shrink 构建通过。
