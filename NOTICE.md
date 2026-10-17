# 第三方组件与许可

本项目的应用代码采用 MIT License。以下第三方组件通过 Gradle/Maven 引入，分别遵循其原始许可证；发布时请保留相应声明。

## java-fsrs

- 项目：Open Spaced Repetition — java-fsrs
- 版本：1.0.0
- 用途：FSRS-5 间隔复习调度
- 许可证：MIT License
- 仓库：https://github.com/open-spaced-repetition/java-fsrs
- Maven：`io.github.open-spaced-repetition:fsrs:1.0.0`

## Jackson

- 项目：FasterXML Jackson Databind / JSR-310
- 版本：2.15.3（Databind / JSR-310）
- 用途：java-fsrs 的可选 JSON 序列化依赖（本项目不调用其 JSON API，但保留运行时依赖以满足库的类引用）
- 许可证：Apache License 2.0
- 仓库：https://github.com/FasterXML/jackson

## AndroidX / Jetpack Compose / Room / DataStore / Lifecycle

- 版权：The Android Open Source Project
- 许可证：Apache License 2.0
- 项目：https://developer.android.com/jetpack

## Kotlin / Kotlin Coroutines / kotlinx.serialization

- 版权：JetBrains s.r.o. and Kotlin Programming Language contributors
- 许可证：Apache License 2.0
- 项目：https://kotlinlang.org/

## 测试依赖

- JUnit 4：Eclipse Public License 1.0
- Robolectric：MIT License
- AndroidX Test：Apache License 2.0

## 题库与物理内容

公式是数学/物理事实，题库中的中文表述、解析、干扰项和数据结构为本项目原创整理。内容参考人民教育出版社普通高中物理教材（2019 版）的章节体系；教材名称、章节名称及相关商标归其权利人所有。本项目与人民教育出版社不存在隶属或背书关系。
