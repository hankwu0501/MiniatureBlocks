package de.studiocode.miniatureblocks.resourcepack.model

class Point2D(var x: Double, var y: Double) {
    
    fun rotateClockwise() {
        if (x == 0.0 && y == 0.0) return
        
        if (x > 0 && y > 0) { // top right
            y = -y
        } else if (x > 0 && y < 0) { // bottom right
            x = -x
        } else if (x < 0 && y < 0) { // bottom left
            y = -y
        } else if (x < 0 && y > 0) { // top left
            x = -x
        } else if (y == 0.0) { // on x axis
            y = -x
            x = 0.0
        } else if (x == 0.0) { // on y axis
            x = y
            y = 0.0
        }
    }
    
    override fun toString() = "Point2D ($x | $y)"
    
}