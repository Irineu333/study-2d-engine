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
        'MouseButton': MouseButton,
        'CollisionObject2D': CollisionObject2D,
        'Area2D': Area2D,
        'PhysicsBody2D': PhysicsBody2D,
        'StaticBody2D': StaticBody2D,
        'CharacterBody2D': CharacterBody2D,
        'CollisionShape2D': CollisionShape2D,
        'Shape2D': Shape2D,
        'RectangleShape2D': RectangleShape2D,
        'CircleShape2D': CircleShape2D,
        'Node2D': Node2D,
        'Camera2D': Camera2D,
        'ColorRect': ColorRect,
        'Circle2D': Circle2D,
        'Line2D': Line2D,
        'Polygon2D': Polygon2D,
        'Label': Label,
        'Signal': Signal,
        'signal': signal,
        # Cross-script handle lookup: `script_of(node)` returns the Python
        # `_ScriptNode` wrapper for the given host Node, exposing its
        # top-level `def`s as bound methods. Forwarded from the Polyglot
        # binding so user scripts pick it up as a regular global.
        'script_of': script_of,
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

        # Skip Signal declarations — handled by _nengine_inspect_signals.
        if isinstance(ann, _ast.Name) and ann.id == 'Signal':
            continue

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


def _nengine_inspect_signals(source, path):
    """Second AST pass dedicated to top-level `name: Signal = signal(...)`
    declarations. Returns a list of SimpleNamespace(name=str).

    Strict validation: if a top-level AnnAssign declares the type `Signal`
    but its value is not a call to `signal(...)`, raise — that almost
    certainly indicates a typo we should surface, not silently drop.
    """
    try:
        tree = _ast.parse(source)
    except Exception:
        return []

    result = []
    for node in tree.body:
        if not isinstance(node, _ast.AnnAssign) or not isinstance(node.target, _ast.Name):
            continue
        ann = node.annotation
        if not (isinstance(ann, _ast.Name) and ann.id == 'Signal'):
            continue
        name = node.target.id
        value = node.value
        is_signal_call = (
            isinstance(value, _ast.Call)
            and isinstance(value.func, _ast.Name)
            and value.func.id == 'signal'
        )
        if not is_signal_call:
            raise SyntaxError(
                "Script '%s' line %d: signal '%s' must be initialized via signal(...)" % (
                    path, getattr(node, 'lineno', 0), name,
                )
            )
        result.append(_NS(name=name))
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


class _SignalProxy:
    """Python-side wrapper around a Kotlin `Signal<T>` that adapts Python
    callables to the Kotlin `(T) -> Unit` handler signature.

    `connect(handler)` registers an adapter on the underlying Kotlin signal:
      - for `Signal<Unit>` (`is_unit=True`), the adapter ignores the emitted
        Unit and calls `handler()` with zero arguments — matching the user's
        expectation of `def my_handler(): ...`.
      - for `Signal<T>` with non-Unit `T`, the adapter forwards the emitted
        value as a single positional argument.

    `disconnect(handler)` removes the adapter via the `Disposable` returned
    by `Signal.connect` (matched by Python `id(handler)`), avoiding
    Polyglot's lambda-equality pitfalls. Exceptions raised by `handler`
    propagate out of `Signal.emit` — consistent with the engine-wide
    fail-fast policy for script errors.
    """

    def __init__(self, kotlin_signal, is_unit):
        object.__setattr__(self, '_signal', kotlin_signal)
        object.__setattr__(self, '_is_unit', bool(is_unit))
        object.__setattr__(self, '_disposables', {})

    def connect(self, handler):
        is_unit = object.__getattribute__(self, '_is_unit')
        disposables = object.__getattribute__(self, '_disposables')
        signal = object.__getattribute__(self, '_signal')
        if is_unit:
            def adapter(_v):
                handler()
        else:
            def adapter(v):
                handler(v)
        d = signal.connect(adapter)
        disposables[id(handler)] = d
        return d

    def disconnect(self, handler):
        disposables = object.__getattribute__(self, '_disposables')
        d = disposables.pop(id(handler), None)
        if d is not None:
            d.dispose()


class _ScriptNode:
    """
    Wraps the host Kotlin `Node` so that scripts can read engine-side
    accessors (`self.transform`, `self.world().position`) and also keep
    per-instance Python state (anything starting with `_`).

    Attribute lookup order on `self.<name>`:
      1. Instance dict (set via setExport, hooks, or `self.foo = ...`).
      2. `_SignalProxy` for any Kotlin `Signal<*>` `val` field declared on
         the underlying Node (discovered via reflection at construction).
      3. Top-level `def`s in the script module — exposed as bound
         methods so peer scripts can call `score.increment()` after
         retrieving the wrapper via `script_of(node)`.
      4. The underlying Kotlin Node (`self.transform`, `self.findChild`,
         ...).

    `self._node` is the explicit handle to the underlying Kotlin `Node`
    — scripts pass it to APIs that take a `Node` (e.g.
    `self.target.resolve(self._node)`) since Polyglot doesn't unwrap the
    Python wrapper automatically.
    """

    def __init__(self, node, module_ns):
        object.__setattr__(self, '_node', node)
        object.__setattr__(self, '_module_ns', module_ns)
        # Reflection-based discovery of `Signal<*>` val fields on the
        # underlying Kotlin Node. Each is wrapped in a `_SignalProxy` so
        # `wrapper.<signal_name>.connect(python_handler)` works uniformly
        # regardless of `Signal<T>`'s erased `T`.
        proxies = {}
        try:
            sig_infos = _nengine_discover_signals(node)
            n = len(sig_infos)
            for i in range(n):
                info = sig_infos[i]
                proxies[info.name] = _SignalProxy(info.signal, info.isUnit)
        except Exception:
            pass
        object.__setattr__(self, '_signal_proxies', proxies)

    def __getattr__(self, name):
        proxies = object.__getattribute__(self, '_signal_proxies')
        if name in proxies:
            return proxies[name]
        module_ns = object.__getattribute__(self, '_module_ns')
        if hasattr(module_ns, name):
            value = getattr(module_ns, name)
            if callable(value) and not isinstance(value, type):
                wrapper = self
                def _bound(*args, **kwargs):
                    return value(wrapper, *args, **kwargs)
                return _bound
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


def _nengine_create_instance(node, module_ns):
    return _ScriptNode(node, module_ns)


def _nengine_bare_wrapper(node):
    """Returns a `_ScriptNode` for a Node without an attached script. Used by
    `script_of(node)` so callers get uniform access to Kotlin-declared
    Signal fields (e.g. `script_of(timer).timeout`) regardless of whether
    the Node has a Python script attached.
    """
    return _ScriptNode(node, _NS())
