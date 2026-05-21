import com.neoutils.engine.games.pong.GoalSide
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

class Ball : BoxCollider() {

    @Inspect
    var ballSize: Float = 16f

    @Inspect
    var initialSpeed: Float = 280f

    @Inspect
    var maxSpeed: Float = 560f

    @Inspect
    var speedupPerHit: Float = 1.05f

    @Inspect
    var fieldCenter: Vec2 = Vec2(400f, 300f)

    /** Emitted on every scoring event with the side that scored. */
    @Transient
    val onScore: Signal<GoalSide> = Signal()

    @Transient
    var velocity: Vec2 = Vec2.ZERO
        private set

    @Transient
    private var random: Random = Random.Default

    @Transient
    private var scoredThisTick: Boolean = false

    @Transient
    private var initialized: Boolean = false

    fun setRandom(rng: Random) {
        random = rng
    }

    override fun onEnter() {
        size = Vec2(ballSize, ballSize)
        if (!initialized) {
            reset(serveToward = if (random.nextBoolean()) 1f else -1f)
            initialized = true
        }
    }

    override fun onUpdate(dt: Float) {
        scoredThisTick = false
        size = Vec2(ballSize, ballSize)
        transform = transform.copy(position = transform.position + velocity * dt)
    }

    override fun onRender(renderer: Renderer) {
        val center = worldPosition() + Vec2(ballSize / 2f, ballSize / 2f)
        renderer.drawCircle(center, radius = ballSize / 2f, color = Color.WHITE, filled = true)
    }

    override fun onCollide(other: Collider) {
        if (scoredThisTick) return
        val otherClassName = other::class.java.simpleName
        when (otherClassName) {
            "Goal" -> {
                val sideMethod = other::class.java.getMethod("getSide")
                val sideValue = sideMethod.invoke(other) as GoalSide
                val scorer = if (sideValue == GoalSide.Left) GoalSide.Right else GoalSide.Left
                onScore.emit(scorer)
                reset(serveToward = if (sideValue == GoalSide.Left) 1f else -1f)
                scoredThisTick = true
            }
            "Wall" -> {
                velocity = velocity.copy(y = -velocity.y)
            }
            "PaddleCollider" -> {
                val paddleBounds = other.bounds()
                val paddleCenterY = paddleBounds.top + paddleBounds.size.y / 2f
                val ballCenterY = transform.position.y + ballSize / 2f
                val relative = ((ballCenterY - paddleCenterY) /
                    (paddleBounds.size.y / 2f)).coerceIn(-1f, 1f)

                val newSpeed = (velocity.length * speedupPerHit).coerceAtMost(maxSpeed)
                val horizontalSign = if (velocity.x > 0f) -1f else 1f
                val maxAngleRad = (PI / 3f).toFloat()
                val angle = relative * maxAngleRad
                velocity = Vec2(
                    horizontalSign * newSpeed * cos(angle),
                    newSpeed * sin(angle),
                )

                val ballPos = transform.position
                val ballRight = ballPos.x + ballSize
                val ballLeft = ballPos.x
                val shift = if (horizontalSign < 0f) paddleBounds.left - ballRight - 0.5f
                else paddleBounds.right - ballLeft + 0.5f
                transform = transform.copy(position = ballPos.copy(x = ballPos.x + shift))
            }
        }
    }

    fun reset(serveToward: Float) {
        size = Vec2(ballSize, ballSize)
        transform = transform.copy(position = fieldCenter - Vec2(ballSize / 2f, ballSize / 2f))
        val angle = (random.nextFloat() - 0.5f) * 1.4f
        val sx = if (serveToward >= 0f) 1f else -1f
        velocity = Vec2(
            sx * initialSpeed * cos(angle),
            initialSpeed * sin(angle),
        )
    }
}
