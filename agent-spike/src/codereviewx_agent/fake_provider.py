from __future__ import annotations

from copy import deepcopy


class FakeProvider:
    def __init__(self, scripted_actions: list[dict]):
        self._actions = deepcopy(scripted_actions)
        self._index = 0

    def next_action(self, observations: list[dict]) -> dict:
        del observations
        if self._index >= len(self._actions):
            return {"action": "finish", "arguments": {"findings": []}}
        action = self._actions[self._index]
        self._index += 1
        return deepcopy(action)
