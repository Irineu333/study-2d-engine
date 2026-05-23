import ast as _ast
from types import SimpleNamespace as _NS

_SUPPORTED_TYPES = frozenset(['int', 'float', 'bool', 'str', 'Vec2', 'Color', 'Rect', 'NodeRef', 'Key'])
_KOTLIN_TYPE_NAMES = {
    'int': 'Int', 'float': 'Float', 'bool': 'Boolean', 'str': 'String',
    'Vec2': 'Vec2', 'Color': 'Color', 'Rect': 'Rect', 'NodeRef': 'NodeRef', 'Key': 'Key',
}


def _nengine_load_module(source, path):
    import builtins
    ns = {
        '__builtins__': builtins,
        '__name__': path,
        'Vec2': Vec2,
        'Color': Color,
        'Rect': Rect,
        'Transform': Transform,
        'NodeRef': NodeRef,
        'Key': Key,
        'BoxCollider': BoxCollider,
        'Node2D': Node2D,
    }
    exec(compile(source, path, 'exec'), ns)
    # Wrap as SimpleNamespace so the Polyglot Value.hasMember/getMember API
    # (attribute lookup) resolves the script's top-level def's; a bare dict
    # exposes its entries as hash keys, not members, and the hook dispatcher
    # would silently no-op every call.
    return _NS(**ns)


def _nengine_inspect(source):
    try:
        tree = _ast.parse(source)
    except Exception:
        return []

    _eval_globals = {
        'Vec2': Vec2, 'Color': Color, 'Rect': Rect,
        'NodeRef': NodeRef, 'Key': Key,
    }

    result = []
    for node in tree.body:
        if not isinstance(node, _ast.AnnAssign) or not isinstance(node.target, _ast.Name):
            continue
        name = node.target.id
        ann = node.annotation
        nullable = False

        # Optional[T]
        if (isinstance(ann, _ast.Subscript) and isinstance(ann.value, _ast.Name)
                and ann.value.id == 'Optional'):
            inner = ann.slice
            if isinstance(inner, _ast.Name) and inner.id in _SUPPORTED_TYPES:
                nullable = True
                ann = inner
            else:
                continue
        # T | None
        elif (isinstance(ann, _ast.BinOp) and isinstance(ann.op, _ast.BitOr)
              and isinstance(ann.right, _ast.Constant) and ann.right.value is None
              and isinstance(ann.left, _ast.Name) and ann.left.id in _SUPPORTED_TYPES):
            nullable = True
            ann = ann.left

        if not (isinstance(ann, _ast.Name) and ann.id in _SUPPORTED_TYPES):
            continue

        type_name = _KOTLIN_TYPE_NAMES[ann.id]

        default_val = None
        if node.value is not None:
            if isinstance(node.value, _ast.Constant):
                default_val = node.value.value
            else:
                try:
                    default_val = eval(_ast.unparse(node.value), _eval_globals)
                except Exception:
                    pass

        # Use SimpleNamespace so Polyglot getMember() works (attribute access, not dict)
        result.append(_NS(name=name, type_name=type_name, nullable=nullable, default=default_val))

    return result


def _nengine_parse_extends(source):
    for line in source.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith('#'):
            rest = stripped[1:].strip()
            if rest.startswith('extends '):
                return rest[len('extends '):].strip()
        elif stripped.startswith('"""') and stripped.endswith('"""') and len(stripped) > 6:
            inner = stripped[3:-3].strip()
            if inner.startswith('extends '):
                return inner[len('extends '):].strip()
        break
    return None


class _ScriptNode:
    """
    Wraps the host Kotlin `Node` so that scripts can read engine-side
    accessors (`self.transform`, `self.worldPosition()`) and also keep
    per-instance Python state (anything starting with `_`).

    `self._node` is the explicit handle to the underlying Kotlin `Node`
    — scripts pass it to APIs that take a `Node` (e.g.
    `self.target.resolve(self._node)`) since Polyglot doesn't unwrap the
    Python wrapper automatically.
    """

    def __init__(self, node):
        object.__setattr__(self, '_node', node)

    def __getattr__(self, name):
        node = object.__getattribute__(self, '_node')
        return getattr(node, name)

    def __setattr__(self, name, value):
        if name.startswith('_'):
            object.__setattr__(self, name, value)
            return
        node = object.__getattribute__(self, '_node')
        try:
            setattr(node, name, value)
        except Exception:
            object.__setattr__(self, name, value)


def _nengine_create_instance(node):
    return _ScriptNode(node)
