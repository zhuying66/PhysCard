"""Authoring model for the bundled physics formula bank.

The data is intentionally kept in source-controlled Python rather than being generated
by an LLM at build time.  ``generate_seed.py`` validates it and emits the two JSON assets
that ship with the app.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional


@dataclass(frozen=True)
class Variable:
    symbol: str
    name: str
    unit: str


@dataclass(frozen=True)
class Confusion:
    wrong: str
    why: str


@dataclass
class FormulaSeed:
    id: str
    title: str
    module: str
    chapter: str
    latex: str
    conditions: str
    variables: list[Variable]
    confusions: list[Confusion]
    cond_wrong: list[str]
    fill_masked: str
    fill_answer: str
    transform_target: str
    transform_correct: str
    scene: str
    related: list[str] = field(default_factory=list)
    fill_alt: Optional[list[str]] = None
    transform_alt: Optional[list[str]] = None


def V(symbol: str, name: str, unit: str) -> Variable:
    return Variable(symbol, name, unit)


def C(wrong: str, why: str) -> Confusion:
    return Confusion(wrong, why)
