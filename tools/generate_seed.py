#!/usr/bin/env python3
"""Validate the authoring bank and emit the app's offline JSON assets.

Run from the repository root:

    python tools/generate_seed.py

The script intentionally has no third-party dependencies.  It fails loudly on duplicate
IDs, missing related formulas, empty options, duplicate options or incomplete question
type coverage.
"""

from __future__ import annotations

import hashlib
import json
import random
import sys
from pathlib import Path

from data_electromagnetism import FORMULAS as EM
from data_extras import EXTRA_EM, EXTRA_MECHANICS, EXTRA_THERMAL
from data_mechanics import FORMULAS as MECHANICS
from data_optics_modern import FORMULAS as OPTICS_MODERN
from data_waves_thermal import FORMULAS as WAVES_THERMAL
from model import FormulaSeed

ROOT = Path(__file__).resolve().parent.parent
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets"

ALL = (
    MECHANICS
    + EXTRA_MECHANICS
    + WAVES_THERMAL
    + EXTRA_THERMAL
    + EM
    + EXTRA_EM
    + OPTICS_MODERN
)

# Accents stay in the icon's palette family (azure -> teal -> amber) so the module chips
# read as one system instead of six unrelated colours.
MODULES = [
    ("mechanics", "力学", "运动、力、能量与动量", "#2871FE"),
    ("waves", "振动与波", "简谐运动与机械波", "#1E7FD6"),
    ("thermal", "热学", "分子动理论与气体定律", "#0E8C8C"),
    ("electromagnetism", "电磁学", "电场、电路、磁场与电磁感应", "#4A5FD0"),
    ("optics", "光学", "几何光学与光的波动性", "#2B93A8"),
    ("modern", "近代物理", "原子、原子核与波粒二象性", "#B87A2B"),
]

ABILITIES = [
    "FULL_FORMULA",
    "FORMULA_FILL",
    "FORMULA_TRANSFORM",
    "APPLICABLE_CONDITION",
    "CONFUSION_DISCRIMINATION",
]

ABILITY_TITLES = {
    "FULL_FORMULA": "完整公式",
    "FORMULA_FILL": "公式填空",
    "FORMULA_TRANSFORM": "公式变形",
    "APPLICABLE_CONDITION": "适用条件",
    "CONFUSION_DISCRIMINATION": "易混辨析",
}

PROMPT_VARIANTS = {
    "FULL_FORMULA": [
        "下列哪一项是「{title}」？",
        "关于「{title}」，正确的表达式是：",
    ],
    "FORMULA_FILL": [
        "补全公式中缺失的部分：",
        "下面公式的空缺处应填入：",
    ],
    "FORMULA_TRANSFORM": [
        "由上式求解 {target}，正确的变形是：",
        "把公式变形为求 {target} 的形式，正确的是：",
    ],
    "APPLICABLE_CONDITION": [
        "关于「{title}」的适用条件和使用方法，下列说法正确的是：",
        "「{title}」在什么条件下成立？使用时又要注意什么？",
    ],
    "CONFUSION_DISCRIMINATION": [
        "{scene}\n应选用下列哪个公式？",
        "{scene}\n下列公式中最合适的是：",
    ],
}


GREEK_NAMES = {
    "alpha": "α",
    "beta": "β",
    "gamma": "γ",
    "delta": "δ",
    "Delta": "Δ",
    "epsilon": "ε",
    "varepsilon": "ε",
    "theta": "θ",
    "lambda": "λ",
    "mu": "μ",
    "nu": "ν",
    "pi": "π",
    "rho": "ρ",
    "sigma": "σ",
    "tau": "τ",
    "phi": "φ",
    "varphi": "φ",
    "Phi": "Φ",
    "omega": "ω",
    "Omega": "Ω",
}


def plain_latex(value: str) -> str:
    """Small display-only conversion used in prose prompts, not a TeX renderer."""
    import re

    text = value
    text = re.sub(r"\\text\{([^{}]*)\}", r"\1", text)
    text = re.sub(r"\\(?:left|right)", "", text)
    text = re.sub(r"\\frac\{([^{}]*)\}\{([^{}]*)\}", r"\1/\2", text)
    text = re.sub(r"\\sqrt\{([^{}]*)\}", r"√(\1)", text)
    for name, symbol in GREEK_NAMES.items():
        text = text.replace("\\" + name, symbol)
    text = text.replace("\\cdot", "·").replace("\\times", "×")
    text = re.sub(r"_\{([^{}]*)\}", r"\1", text)
    text = text.replace("_", "")
    text = text.replace("^2", "²").replace("^3", "³")
    text = text.replace("{", "").replace("}", "").replace("\\", "")
    text = re.sub(r"([\u0391-\u03a9\u03b1-\u03c9])\s+([A-Za-z])", r"\1\2", text)
    return text.strip()


def stable_int(text: str) -> int:
    return int(hashlib.sha256(text.encode("utf-8")).hexdigest()[:12], 16)


def shuffled_with_correct(options: list[dict], correct: int, seed_text: str) -> tuple[list[dict], int]:
    indexed = list(enumerate(options))
    correct_option = indexed[correct]
    rest = [item for item in indexed if item[0] != correct]
    random.Random(stable_int(seed_text)).shuffle(rest)
    ordered = rest[:]
    position = stable_int(seed_text + ":pos") % (len(rest) + 1)
    ordered.insert(position, correct_option)
    return [item[1] for item in ordered], position


def unique_options(options: list[dict], label: str) -> list[dict]:
    seen: set[str] = set()
    result: list[dict] = []
    for option in options:
        key = option.get("latex") or option.get("text")
        if not key:
            raise ValueError(f"{label}: empty option")
        if key in seen:
            raise ValueError(f"{label}: duplicate option {key!r}")
        seen.add(key)
        result.append(option)
    return result


def option(latex: str | None = None, text: str | None = None) -> dict:
    value: dict[str, str] = {}
    if latex is not None:
        value["latex"] = latex
    if text is not None:
        value["text"] = text
    return value


def pick_unique(candidates: list[dict], exclude: set[str], count: int, label: str) -> list[dict]:
    """Return [count] distractors that never repeat the answer or each other.

    Distractors are always taken from real authoring data (sibling formulas or the
    formula's own confusion list), never invented, so a wrong option is always a
    mistake a student could genuinely make.
    """
    picked: list[dict] = []
    for item in candidates:
        key = item.get("latex") or item.get("text")
        if not key or key in exclude:
            continue
        exclude.add(key)
        picked.append(item)
        if len(picked) == count:
            return picked
    raise ValueError(f"{label}: only {len(picked)} unique distractors available, need {count}")


def all_confusion_whys() -> list[dict]:
    return [option(text=confusion.why) for formula in ALL for confusion in formula.confusions]


def find_formula(formula_id: str) -> FormulaSeed:
    for formula in ALL:
        if formula.id == formula_id:
            return formula
    raise KeyError(formula_id)


def related_formulas(formula: FormulaSeed) -> list[FormulaSeed]:
    chosen: list[FormulaSeed] = []
    for related_id in formula.related:
        candidate = find_formula(related_id)
        if candidate.id != formula.id and candidate not in chosen:
            chosen.append(candidate)
    if len(chosen) < 3:
        same_chapter = [
            candidate
            for candidate in ALL
            if candidate.module == formula.module
            and candidate.chapter == formula.chapter
            and candidate.id != formula.id
            and candidate not in chosen
        ]
        for candidate in same_chapter:
            chosen.append(candidate)
            if len(chosen) == 3:
                break
    if len(chosen) < 3:
        same_module = [
            candidate
            for candidate in ALL
            if candidate.module == formula.module
            and candidate.id != formula.id
            and candidate not in chosen
        ]
        for candidate in same_module:
            chosen.append(candidate)
            if len(chosen) == 3:
                break
    if len(chosen) < 3:
        raise ValueError(f"{formula.id}: not enough formulas for distractors")
    return chosen[:3]


def validate() -> None:
    errors: list[str] = []
    ids: set[str] = set()
    module_ids = {module[0] for module in MODULES}
    for formula in ALL:
        if formula.id in ids:
            errors.append(f"duplicate formula id: {formula.id}")
        ids.add(formula.id)
        if formula.module not in module_ids:
            errors.append(f"{formula.id}: unknown module {formula.module}")
        if not formula.latex.strip():
            errors.append(f"{formula.id}: empty formula")
        if not formula.conditions.strip():
            errors.append(f"{formula.id}: empty conditions")
        if len(formula.variables) < 2:
            errors.append(f"{formula.id}: fewer than 2 variables")
        if not formula.scene.strip():
            errors.append(f"{formula.id}: empty scene")
        if "\\boxed{?}" not in formula.fill_masked:
            errors.append(f"{formula.id}: fill mask must contain \\boxed{{?}}")
        if not formula.fill_answer.strip():
            errors.append(f"{formula.id}: empty fill answer")
        if not formula.transform_correct.strip():
            errors.append(f"{formula.id}: empty transform correct")
        if len(formula.cond_wrong) != 3:
            errors.append(f"{formula.id}: needs exactly 3 condition distractors")
        for i, confusion in enumerate(formula.confusions):
            if not confusion.wrong.strip() or not confusion.why.strip():
                errors.append(f"{formula.id}: empty confusion at {i}")
            if confusion.wrong.strip() == formula.latex.strip():
                errors.append(f"{formula.id}: confusion repeats the correct formula")
        for related_id in formula.related:
            if related_id not in ids and related_id not in {f.id for f in ALL}:
                errors.append(f"{formula.id}: unknown related id {related_id}")
        if formula.related and formula.id in formula.related:
            errors.append(f"{formula.id}: related to itself")
        for variable in formula.variables:
            if not variable.symbol or not variable.name or not variable.unit:
                errors.append(f"{formula.id}: incomplete variable {variable}")
    for module_id, name, _, _ in MODULES:
        if not any(formula.module == module_id for formula in ALL):
            errors.append(f"module {name} has no formulas")
    if errors:
        print("Seed validation failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        raise SystemExit(1)


def _distinct_rotations(pool: list, size: int, wanted: int, offset: int = 0) -> list[list]:
    """Yield up to [wanted] genuinely different combinations drawn from [pool].

    Two combinations count as the same when they contain the same members, so the
    result never repeats an option set with only the order changed.
    """
    total = len(pool)
    if total < size:
        return []
    seen: set[tuple[str, ...]] = set()
    result: list[list] = []
    for step in range(total):
        indices = [(offset + step + shift) % total for shift in range(size)]
        if len(set(indices)) != size:
            continue
        members = [pool[index] for index in indices]
        key = tuple(sorted(_member_key(member) for member in members))
        if key in seen:
            continue
        seen.add(key)
        result.append(members)
        if len(result) == wanted:
            break
    return result


def _member_key(member) -> str:
    if isinstance(member, FormulaSeed):
        return member.id
    return member.get("latex") or member.get("text") or ""


def build_questions(formula: FormulaSeed, order_index: int) -> list[dict]:
    """Emit every question variant the authoring data can support for one formula.

    Each ability yields several questions instead of one. All options come from authored
    content (sibling formulas, the formula's own confusion list, its written condition
    distractors, or another formula's condition distractors) — nothing is invented, so a
    wrong option is always a mistake a student could really make.
    """
    related = related_formulas(formula)
    correct_formula = option(latex=formula.latex)
    questions: list[dict] = []

    # Distractor candidates, nearest first: same chapter, then same module.
    same_chapter = [
        candidate
        for candidate in ALL
        if candidate.module == formula.module
        and candidate.chapter == formula.chapter
        and candidate.id != formula.id
    ]
    same_module = [
        candidate
        for candidate in ALL
        if candidate.module == formula.module
        and candidate.id != formula.id
        and candidate not in same_chapter
    ]
    formula_pool: list[FormulaSeed] = []
    for candidate in related + same_chapter + same_module:
        if candidate.id != formula.id and candidate not in formula_pool:
            formula_pool.append(candidate)

    # ---------------------------------------------------------------- A. 完整公式
    for index, distractors in enumerate(_distinct_rotations(formula_pool, 3, 3)):
        tag = chr(ord("a") + index)
        options = [correct_formula] + [option(latex=item.latex) for item in distractors]
        ordered, correct_index = shuffled_with_correct(options, 0, formula.id + ":full:" + tag)
        names = "、".join(f"「{item.title}」" for item in distractors)
        questions.append(
            {
                "id": f"q_{formula.id}_full_{tag}",
                "formulaId": formula.id,
                "ability": "FULL_FORMULA",
                "prompt": PROMPT_VARIANTS["FULL_FORMULA"][(order_index + index) % 2].format(
                    title=formula.title
                ),
                "options": unique_options(ordered, formula.id + ":full:" + tag),
                "correctIndex": correct_index,
                "explanation": (
                    f"{formula.title}的标准形式是 {plain_latex(formula.latex)}。"
                    f"另外三个选项分别对应{names}，变量含义和适用情境并不相同。"
                    f"使用条件：{formula.conditions}"
                ),
            }
        )

    # ---------------------------------------------------------------- B. 公式填空
    fill_alt = formula.fill_alt or []
    if len(fill_alt) < 3:
        raise ValueError(f"{formula.id}: needs 3 fill alternatives")
    # The blank asks for one fragment of this formula. Distractors are authored wrong
    # fragments: this formula's own alternatives first, then same-chapter and same-module
    # neighbours (a student who mixes up related formulas really does produce these).
    fill_candidates = [option(latex=item) for item in fill_alt]
    for other in same_chapter + same_module:
        for item in other.fill_alt or []:
            fill_candidates.append(option(latex=item))
    fill_deduped: list[dict] = []
    for candidate in fill_candidates:
        key = candidate["latex"]
        if key == formula.fill_answer:
            continue
        if any(existing["latex"] == key for existing in fill_deduped):
            continue
        fill_deduped.append(candidate)
    fill_pools = _distinct_rotations(fill_deduped, 3, 3)
    if not fill_pools:
        raise ValueError(f"{formula.id}: fill alternatives cannot form a pool")
    for index, distractors in enumerate(fill_pools):
        tag = chr(ord("a") + index)
        options = [option(latex=formula.fill_answer)] + distractors
        ordered, correct_index = shuffled_with_correct(options, 0, formula.id + ":fill:" + tag)
        questions.append(
            {
                "id": f"q_{formula.id}_fill_{tag}",
                "formulaId": formula.id,
                "ability": "FORMULA_FILL",
                "prompt": PROMPT_VARIANTS["FORMULA_FILL"][(order_index + index) % 2],
                "stimulusLatex": formula.fill_masked,
                "options": unique_options(ordered, formula.id + ":fill:" + tag),
                "correctIndex": correct_index,
                "explanation": (
                    f"补全后的公式为 {plain_latex(formula.latex)}，空缺处是 {plain_latex(formula.fill_answer)}。"
                    f"其他选项改变了运算结构或系数，会导致量纲或数量关系错误。"
                    f"适用条件：{formula.conditions}"
                ),
            }
        )

    # ---------------------------------------------------------------- C. 公式变形
    transform_alt = formula.transform_alt or []
    if len(transform_alt) < 3:
        raise ValueError(f"{formula.id}: needs 3 transform alternatives")
    # Rearranging this formula. Distractors are authored wrong rearrangements: this
    # formula's own first, then neighbours from the same chapter and module.
    transform_candidates = [option(latex=item) for item in transform_alt]
    for other in same_chapter + same_module:
        for item in other.transform_alt or []:
            transform_candidates.append(option(latex=item))
    transform_deduped: list[dict] = []
    for candidate in transform_candidates:
        key = candidate["latex"]
        if key == formula.transform_correct:
            continue
        if any(existing["latex"] == key for existing in transform_deduped):
            continue
        transform_deduped.append(candidate)
    transform_pools = _distinct_rotations(transform_deduped, 3, 3)
    if not transform_pools:
        raise ValueError(f"{formula.id}: transform alternatives cannot form a pool")
    for index, distractors in enumerate(transform_pools):
        tag = chr(ord("a") + index)
        options = [option(latex=formula.transform_correct)] + distractors
        ordered, correct_index = shuffled_with_correct(options, 0, formula.id + ":transform:" + tag)
        questions.append(
            {
                "id": f"q_{formula.id}_transform_{tag}",
                "formulaId": formula.id,
                "ability": "FORMULA_TRANSFORM",
                "prompt": PROMPT_VARIANTS["FORMULA_TRANSFORM"][(order_index + index) % 2].format(
                    target=plain_latex(formula.transform_target)
                ),
                "stimulusLatex": formula.latex,
                "options": unique_options(ordered, formula.id + ":transform:" + tag),
                "correctIndex": correct_index,
                "explanation": (
                    f"由 {plain_latex(formula.latex)} 解得 {plain_latex(formula.transform_correct)}。"
                    f"注意移项、除以系数和开平方时不能改变等式关系。"
                ),
            }
        )

    # ---------------------------------------------------------------- D. 适用条件
    # Wrong statements come from: this formula's own written distractors, this formula's
    # confusion explanations, then other formulas' condition distractors (same module
    # first). All of them are real misstatements, never invented text.
    condition_candidates: list[dict] = []
    for text in formula.cond_wrong:
        condition_candidates.append(option(text=text))
    for confusion in formula.confusions:
        condition_candidates.append(option(text=confusion.why))
    for other in same_chapter + same_module:
        for text in other.cond_wrong:
            condition_candidates.append(option(text=text))
    deduped: list[dict] = []
    for candidate in condition_candidates:
        text = candidate["text"]
        if text == formula.conditions:
            continue
        if any(existing["text"] == text for existing in deduped):
            continue
        deduped.append(candidate)
    condition_pools = _distinct_rotations(deduped, 3, 3)
    if not condition_pools:
        raise ValueError(f"{formula.id}: no condition distractors available")
    for index, distractors in enumerate(condition_pools):
        tag = chr(ord("a") + index)
        options = [option(text=formula.conditions)] + distractors
        ordered, correct_index = shuffled_with_correct(options, 0, formula.id + ":condition:" + tag)
        questions.append(
            {
                "id": f"q_{formula.id}_condition_{tag}",
                "formulaId": formula.id,
                "ability": "APPLICABLE_CONDITION",
                "prompt": PROMPT_VARIANTS["APPLICABLE_CONDITION"][(order_index + index) % 2].format(
                    title=formula.title
                ),
                "stimulusLatex": formula.latex,
                "options": unique_options(ordered, formula.id + ":condition:" + tag),
                "correctIndex": correct_index,
                "explanation": (
                    f"正确表述：{formula.conditions} "
                    f"其余选项都越过了公式的适用范围，或把符号、单位和因果关系弄反了。"
                ),
            }
        )

    # ---------------------------------------------------------------- E. 易混辨析
    for index, distractors in enumerate(_distinct_rotations(formula_pool, 3, 3, offset=1)):
        tag = chr(ord("a") + index)
        options = [correct_formula] + [option(latex=item.latex) for item in distractors]
        ordered, correct_index = shuffled_with_correct(
            options, 0, formula.id + ":discriminate:" + tag
        )
        questions.append(
            {
                "id": f"q_{formula.id}_discriminate_{tag}",
                "formulaId": formula.id,
                "ability": "CONFUSION_DISCRIMINATION",
                "prompt": PROMPT_VARIANTS["CONFUSION_DISCRIMINATION"][(order_index + index) % 2].format(
                    scene=formula.scene
                ),
                "options": unique_options(ordered, formula.id + ":discriminate:" + tag),
                "correctIndex": correct_index,
                "explanation": (
                    f"该情境对应「{formula.title}」，公式为 {plain_latex(formula.latex)}。"
                    f"其他选项来自相近但不同的物理规律，不能直接套用。"
                    f"关键条件：{formula.conditions}"
                ),
            }
        )

    return questions


def main() -> None:
    validate()
    ids = {formula.id for formula in ALL}
    for formula in ALL:
        for related_id in formula.related:
            if related_id not in ids:
                raise ValueError(f"{formula.id}: invalid related id {related_id}")

    ASSET_DIR.mkdir(parents=True, exist_ok=True)

    formula_records = []
    question_records = []
    for order_index, formula in enumerate(ALL):
        formula_records.append(
            {
                "id": formula.id,
                "title": formula.title,
                "module": formula.module,
                "chapter": formula.chapter,
                "textbook": "人教版高中物理（2019 版）",
                "latex": formula.latex,
                "conditions": formula.conditions,
                "variables": [
                    {"symbol": item.symbol, "name": item.name, "unit": item.unit}
                    for item in formula.variables
                ],
                "confusions": [
                    {"wrong": item.wrong, "why": item.why} for item in formula.confusions
                ],
                "relatedIds": formula.related,
                "orderIndex": order_index,
            }
        )
        question_records.extend(build_questions(formula, order_index))

    modules = [
        {
            "id": module_id,
            "name": name,
            "description": description,
            "accent": accent,
            "orderIndex": index,
        }
        for index, (module_id, name, description, accent) in enumerate(MODULES)
    ]

    (ASSET_DIR / "formulas.json").write_text(
        json.dumps(formula_records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (ASSET_DIR / "questions.json").write_text(
        json.dumps(question_records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (ASSET_DIR / "modules.json").write_text(
        json.dumps(modules, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    print(f"Formulas: {len(formula_records)}")
    print(f"Questions: {len(question_records)}")
    print(f"Modules: {len(modules)}")
    for module_id, name, _, _ in MODULES:
        count = sum(1 for formula in ALL if formula.module == module_id)
        print(f"  {name}: {count} formulas")
    print(f"Written to {ASSET_DIR}")


if __name__ == "__main__":
    main()
