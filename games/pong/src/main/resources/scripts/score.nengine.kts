import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

class Score : Node2D() {

    @Inspect
    var textSize: Float = 48f

    @Inspect
    var color: Color = Color.WHITE

    @Transient
    var value: Int = 0
        private set

    fun increment() {
        value++
    }

    override fun onRender(renderer: Renderer) {
        renderer.drawText(value.toString(), worldPosition(), textSize, color)
    }
}
