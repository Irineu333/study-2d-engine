class Goal : BoxCollider() {
    @Inspect
    var side: Side = Side.Left

    enum class Side {
        Left,
        Right
    }
}
