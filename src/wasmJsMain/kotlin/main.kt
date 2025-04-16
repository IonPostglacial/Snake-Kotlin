import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.CanvasRenderingContext2D
import kotlin.random.Random

private const val CELL_SIZE = 10
private const val GRID_WIDTH = 40
private const val GRID_HEIGHT = 40

private val COLOR_BACKGROUND = "#000000".toJsString()
private val COLOR_SNAKE      = "#00ff00".toJsString()
private val COLOR_APPLE      = "#ff0000".toJsString()

internal enum class Direction(val dx: Int, val dy: Int) {
  UP(0, -1),
  DOWN(0, 1),
  LEFT(-1, 0),
  RIGHT(1, 0);

  fun isOppositeOf(other: Direction): Boolean =
    (dx == -other.dx) && (dy == -other.dy)
}

internal class Snake {
  internal val xs = IntArray(GRID_WIDTH * GRID_HEIGHT)
  internal val ys = IntArray(GRID_WIDTH * GRID_HEIGHT)

  internal var length: Int = 0
  private var head: Int = 0
  private var direction = Direction.RIGHT

  internal var appleX: Int = 0
  internal var appleY: Int = 0

  internal var stepPeriod: Double = 300.0
  internal var score: Int = 0
  private var nextReward: Int = 10

  fun init() {
    score = 0
    stepPeriod = 300.0
    nextReward = 10

    teleportApple()

    length = 4
    head = 3
    direction = Direction.RIGHT
    xs[0] = 0; ys[0] = 0
    xs[1] = 1; ys[1] = 0
    xs[2] = 2; ys[2] = 0
    xs[3] = 3; ys[3] = 0
  }

  fun snakeWillEatApple(): Boolean {
    val hx = xs[head]
    val hy = ys[head]
    return ((hx + direction.dx) == appleX && (hy + direction.dy) == appleY)
  }

  fun snakeEatsItself(): Boolean {
    for (i in 0 until length) {
      if (i == head) continue
      if (xs[head] == xs[i] && ys[head] == ys[i]) return true
    }
    return false
  }

  fun snakeIsOutOfBounds(): Boolean {
    val hx = xs[head]
    val hy = ys[head]
    return hx < 0 || hx >= GRID_WIDTH || hy < 0 || hy >= GRID_HEIGHT
  }

  fun snakeMoveAhead() {
    val hx = xs[head]
    val hy = ys[head]
    val nextX = hx + direction.dx
    val nextY = hy + direction.dy

    head = if (head == length - 1) 0 else head + 1
    xs[head] = nextX
    ys[head] = nextY
  }

  fun snakeGrow() {
    val hx = xs[head]
    val hy = ys[head]
    val nextX = hx + direction.dx
    val nextY = hy + direction.dy

    if (head == length - 1) {
      xs[length] = nextX
      ys[length] = nextY
    } else {
      for (i in length downTo (head + 1)) {
        xs[i] = xs[i - 1]
        ys[i] = ys[i - 1]
      }
      xs[head + 1] = nextX
      ys[head + 1] = nextY
    }
    length++
    head++
  }

  fun changeSnakeDirection(d: Direction) {
    if (!direction.isOppositeOf(d)) {
      direction = d
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

private fun paintBackground(cx: CanvasRenderingContext2D) {
  cx.fillStyle = COLOR_BACKGROUND
  cx.fillRect(
    0.0, 0.0,
    (GRID_WIDTH * CELL_SIZE).toDouble(),
    (GRID_HEIGHT * CELL_SIZE).toDouble()
  )
}

private fun paintSnake(cx: CanvasRenderingContext2D, snake: Snake) {
  cx.fillStyle = COLOR_SNAKE
  for (i in 0 until snake.length) {
    cx.fillRect(
      (snake.xs[i] * CELL_SIZE).toDouble(),
      (snake.ys[i] * CELL_SIZE).toDouble(),
      CELL_SIZE.toDouble(),
      CELL_SIZE.toDouble()
    )
  }
}

private fun paintApple(cx: CanvasRenderingContext2D, snake: Snake) {
  cx.fillStyle = COLOR_APPLE
  cx.fillRect(
    (snake.appleX * CELL_SIZE).toDouble(),
    (snake.appleY * CELL_SIZE).toDouble(),
    CELL_SIZE.toDouble(),
    CELL_SIZE.toDouble()
  )
}

private fun repaint(cx: CanvasRenderingContext2D, snake: Snake) {
  paintBackground(cx)
  paintSnake(cx, snake)
  paintApple(cx, snake)
  cx.fill()
}

fun main() {
  val canvas = document.querySelector("#gamescreen") as? HTMLCanvasElement
    ?: error("Cannot find #gamescreen")
  val cx = canvas.getContext("2d") as? CanvasRenderingContext2D
    ?: error("Cannot get 2D context")

  canvas.width = GRID_WIDTH * CELL_SIZE
  canvas.height = GRID_HEIGHT * CELL_SIZE

  val scoreDisplay = document.querySelector("#score") as? HTMLElement

  val snake = Snake().apply { init() }

  fun handleKeyDown(event: Event) {
    event.stopPropagation()
    val e = event as KeyboardEvent
    when (e.code) {
      "ArrowUp"    -> snake.changeSnakeDirection(Direction.UP)
      "ArrowDown"  -> snake.changeSnakeDirection(Direction.DOWN)
      "ArrowLeft"  -> snake.changeSnakeDirection(Direction.LEFT)
      "ArrowRight" -> snake.changeSnakeDirection(Direction.RIGHT)
    }
  }

  var lastUpdateTimestamp = -1.0
  fun step(timestamp: Double) {
    if (lastUpdateTimestamp < 0) {
      lastUpdateTimestamp = timestamp
    }
    val progress = timestamp - lastUpdateTimestamp

    if (progress >= snake.stepPeriod) {
      lastUpdateTimestamp = timestamp

      if (snake.snakeWillEatApple()) {
        snake.snakeGrow()
        snake.teleportApple()
        snake.speedUpGame()
        snake.updateScore()
        scoreDisplay?.innerText = snake.score.toString()
      } else {
        snake.snakeMoveAhead()
      }

      if (snake.snakeIsOutOfBounds() || snake.snakeEatsItself()) {
        window.alert("Game Over!")
        window.location.reload()
        return
      }
      repaint(cx, snake)
    }

    window.requestAnimationFrame(::step)
  }

  window.addEventListener("keydown", ::handleKeyDown)

  repaint(cx, snake)
  window.requestAnimationFrame(::step)
}
