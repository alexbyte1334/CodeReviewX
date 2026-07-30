from __future__ import annotations

from typing import Annotated, TypedDict

from langgraph.graph import END, StateGraph

from .loop import BoundedReviewerLoop


class GraphState(TypedDict):
    run_count: Annotated[int, max]
    result: object | None


def build_graph(loop: BoundedReviewerLoop):
    """Wrap the bounded security kernel in a checkpoint-compatible LangGraph."""
    graph = StateGraph(GraphState)
    graph.add_node("reviewer", lambda state: {
        "run_count": state.get("run_count", 0) + 1,
        "result": loop.run(),
    })
    graph.set_entry_point("reviewer")
    graph.add_edge("reviewer", END)
    return graph.compile()
