import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.random.Random

private const val CELL_SIZE   = 10
private const val GRID_WIDTH  = 40
private const val GRID_HEIGHT = 40

private const val COLOR_BACKGROUND = "#000000"
private const val COLOR_SNAKE      = "#00ff00"
private const val COLOR_APPLE      = "#ff0000"

private const val DIR_RIGHT = 0
private const val DIR_DOWN  = 1
private const val DIR_LEFT  = 2
private const val DIR_UP    = 3

private val DX = listOf(1, 0, -1, 0)
private val DY = listOf(0, 1,  0, -1)

private fun isOpposite(d1: Int, d2: Int): Boolean =
  when (d1) {
    DIR_RIGHT -> d2 == DIR_LEFT
    DIR_DOWN  -> d2 == DIR_UP
    DIR_LEFT  -> d2 == DIR_RIGHT
    DIR_UP    -> d2 == DIR_DOWN
    else -> false
  }

private class DirectionsBuffer(val capacity: Int) {
  private val data = ByteArray((capacity + 3) shr 2) // 4 directions per byte

  fun setAt(index: Int, dirCode: Int) {
    val byteIndex = index shr 2
    val shift     = (index and 3) shl 1

    val oldByte = data[byteIndex].toInt() and 0xFF
    val mask    = (3 shl shift).inv() and 0xFF
    val newVal  = (oldByte and mask) or (dirCode shl shift)
    data[byteIndex] = newVal.toByte()
  }

  fun getAt(index: Int): Int {
    val byteIndex = index shr 2
    val shift     = (index and 3) shl 1
    val b         = data[byteIndex].toInt() and 0xFF
    return (b shr shift) and 3
  }
}

private class Snake {
  val directions = DirectionsBuffer(GRID_WIDTH * GRID_HEIGHT)

  var length = 4
    private set

  private var headIndex = length - 2
  var tailIndex = 0
  private var directionCode = DIR_RIGHT

  var headX = 3
  var headY = 0

  var tailX = 0
  var tailY = 0

  var appleX = 0
  var appleY = 0

  var stepPeriod = 300.0
  var score = 0
  private var nextReward = 10

  init {
    teleportApple()
  }

  private fun moveIndexForward(idx: Int): Int =
    if (idx + 1 == directions.capacity) 0 else (idx + 1)

  fun willEatApple(): Boolean {
    val nx = headX + DX[directionCode]
    val ny = headY + DY[directionCode]
    return (nx == appleX && ny == appleY)
  }

  fun isOutOfBounds(): Boolean =
    headX < 0 || headX >= GRID_WIDTH || headY < 0 || headY >= GRID_HEIGHT

  fun eatsItself(): Boolean {
    var cx = tailX
    var cy = tailY
    var idx = tailIndex

    repeat(length - 1) {
      if (cx == headX && cy == headY) return true
      val code = directions.getAt(idx)
      idx = moveIndexForward(idx)
      cx += DX[code]
      cy += DY[code]
    }
    return false
  }

  private fun doMove(isGrowing: Boolean) {
    headIndex = moveIndexForward(headIndex)
    directions.setAt(headIndex, directionCode)

    headX += DX[directionCode]
    headY += DY[directionCode]

    if (!isGrowing) {
      val tailCode = directions.getAt(tailIndex)
      tailX += DX[tailCode]
      tailY += DY[tailCode]
      tailIndex = moveIndexForward(tailIndex)
    } else {
      length++
    }
  }

  fun moveAhead() = doMove(isGrowing = false)
  fun grow()       = doMove(isGrowing = true)

  fun changeDirection(newCode: Int) {
    if (!isOpposite(directionCode, newCode)) {
      directionCode = newCode
    }
  }

  fun teleportApple() {
    appleX = Random.nextInt(GRID_WIDTH)
    appleY = Random.nextInt(GRID_HEIGHT)
  }

  fun speedUpGame() {
    if (stepPeriod > 50) {
      stepPeriod -= 25
    }
  }

  fun updateScore() {
    score += nextReward
    nextReward += 10
  }
}

private fun paintBackground(ctx: CanvasRenderingContext2D) {
  ctx.fillStyle = COLOR_BACKGROUND.toJsString()
  ctx.fillRect(
    x = .0,
    y = .0,
    w = (GRID_WIDTH * CELL_SIZE).toDouble(),
    h = (GRID_HEIGHT * CELL_SIZE).toDouble()
  )
}

private fun paintSnake(ctx: CanvasRenderingContext2D, snake: Snake) {
  ctx.fillStyle = COLOR_SNAKE.toJsString()

  var cx = snake.tailX
  var cy = snake.tailY
  var idx = snake.tailIndex
  repeat(snake.length) { i ->
    ctx.fillRect(
      (cx * CELL_SIZE).toDouble(),
      (cy * CELL_SIZE).toDouble(),
      CELL_SIZE.toDouble(),
      CELL_SIZE.toDouble()
    )
    if (i < snake.length - 1) {
      val code = snake.directions.getAt(idx)
      idx = (if (idx + 1 == snake.directions.capacity) 0 else (idx + 1))
      cx += DX[code]
      cy += DY[code]
    }
  }
}

private fun paintApple(ctx: CanvasRenderingContext2D, snake: Snake) {
  ctx.fillStyle = COLOR_APPLE.toJsString()
  ctx.fillRect(
    x = (snake.appleX * CELL_SIZE).toDouble(),
    y = (snake.appleY * CELL_SIZE).toDouble(),
    w = CELL_SIZE.toDouble(),
    h = CELL_SIZE.toDouble()
  )
}

private fun repaint(ctx: CanvasRenderingContext2D, snake: Snake, gameOver: Boolean) {
  paintBackground(ctx)
  if (gameOver) {
    ctx.fillStyle = "red".toJsString()
    ctx.font = "bold 24px monospace"
    ctx.textAlign = CanvasTextAlign.CENTER
    val centerX = (GRID_WIDTH * CELL_SIZE) / 2.0
    val centerY = (GRID_HEIGHT * CELL_SIZE) / 2.0
    ctx.fillText("OH NO, GAME OVER :(", centerX, centerY)
  } else {
    paintSnake(ctx, snake)
    paintApple(ctx, snake)
  }
}

fun main() {
  val canvas = document.querySelector("#gamescreen") as? HTMLCanvasElement
  if (canvas == null) {
    window.alert("Cannot find #gamescreen")
    return
  }
  canvas.width  = GRID_WIDTH * CELL_SIZE
  canvas.height = GRID_HEIGHT * CELL_SIZE

  val ctx = canvas.getContext("2d") as? CanvasRenderingContext2D
  if (ctx == null) {
    window.alert("Cannot get 2D context")
    return
  }

  val scoreElement = document.querySelector("#score") as? HTMLElement

  var gameOver = false
  var snake = Snake()

  fun handleKeyDown(event: Event) {
    event.preventDefault()
    val e = event.unsafeCast<KeyboardEvent>()
    if (gameOver) {
      snake = Snake()
      gameOver = false
    }
    when (e.code) {
      "ArrowRight" -> snake.changeDirection(DIR_RIGHT)
      "ArrowDown"  -> snake.changeDirection(DIR_DOWN)
      "ArrowLeft"  -> snake.changeDirection(DIR_LEFT)
      "ArrowUp"    -> snake.changeDirection(DIR_UP)
    }
  }

  window.addEventListener("keydown", ::handleKeyDown)

  var lastUpdateTimestamp = -1.0
  fun step(timestamp: Double) {
    if (lastUpdateTimestamp < 0) {
      lastUpdateTimestamp = timestamp
    }
    val progress = timestamp - lastUpdateTimestamp

    if (progress >= snake.stepPeriod) {
      lastUpdateTimestamp = timestamp

      if (snake.willEatApple()) {
        snake.grow()
        snake.teleportApple()
        snake.speedUpGame()
        snake.updateScore()
        scoreElement?.innerText = snake.score.toString()
      } else {
        snake.moveAhead()
      }

      if (snake.isOutOfBounds() || snake.eatsItself()) {
        gameOver = true
      }
      repaint(ctx, snake, gameOver)
    }
    window.requestAnimationFrame(::step)
  }

  repaint(ctx, snake, gameOver)
  window.requestAnimationFrame(::step)
}
