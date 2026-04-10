## Code Model

```scala
type BleScan[A]    = Seq[BleMetric] => Flow[A]
type BleConnect[A] = Seq[BleMetric] => A => Flow[BleConnectEvent[A]]
type BleGather     = Seq[BleMetric] => Flow[BleEvent] 

enum BleConnectEvent[A]:
  case Unsupported(value: A, part: Boolean, metrics: Seq[BleMetric]) // part 为 true 表示部分不支持
  case Notify(value: A, meter: BleMeter)
  case Fatal(value: A, cause: String)

enum BleEvent:
  case Available(name: String, meter: BleMeter)
  case Unavailable

enum BleMetric(val service: UUID, val characteristic: UUID):
  case HeartRate extends Metric(???, ???)
  case RunSpeedCadence extends Metric(???, ???)

type BleMeter = (Metric, Byte[])

object AndroidTransport:
  def scan(context: Context): BleScan[BluetoothDevice] = ???
  def connect(context: Context): BleConnect[BluetoohDevice] = ???

class BleViewModel(
  scan: BleScan[BluetoothDevice], 
  connect: BleConnect[BluetoohDevice]
) extends ViewModel {
  val bleStatus: StateFlow[String] = ??? 
  val heartRate: StateFlow[Option[String]] = ??? // None as Default
  val pace: StateFlow[Option[String]] = ??? // None as Default
  val cadence: StateFlow[Option[String]] = ??? // None as Default

  def run:
    bleStatus = "Scanning"
    gather(viewModelScope, scan, connect)(/* all BleMetric */)
      .onEach(handle)
      .launchInScope(viewModelScope)

  def handle: PartialFunction[BleEvent, Unit] = 
    case Available(value, (HeartRate, data)) => 
      // bleStatus = value
      // heartRate = read(data)
    case Available(value, (RunSpeedCadence, data)) => 
      // bleStatus = value
      // (pace, cadence) = read(data)
    case Unavailable              => 
      // bleStatus = "Tap to Rescan"
      // (heartRate, pace, cadence) => (null, null, null)
}

object BleViewModel {
  type Dispatch[A] = (State[A], Event[A]) => State[A]
  type ToConnect[A] = (D, Seq[BleMetric]) => Job

  def gather[D: BleInfo](
    scope: CoroutineScope, 
    scan: BleScan[A], 
    connect: BleConnect[A]
  ): BleGather = metrics => 
    val bus: Channel[Event] = ???
    
    val fire: ToConnect = (value, metrics) =>
      connect(value, metrics)
        .onEach(e => bus.send(Reply(e)))
        .lauchIn(scope)
      
    channelFlow {
      val handle = dispatch(fire, send)
      var state = State(metrics, Seq(), false, Map())
      val react = bus.asReceiveFlow
        .onEach(e => state = handle(state, e))
        .launchIn(scop)

      val scanning = scan(metrics).take(metrics.size).timeout(5.seconds)
        .onEach(d => bus.send(Found(d)))
        .onCompletion(x => bus.send(NoMoreDevice))
        .launchIn(scope)

      awaitClose {
        scanning.cancel()
        react.cancel()
        state.jobs.forech(_.cancel())
      }  
    }


  def dispatch[D: BleInfo](fire: ToConnect[A], send: BleEvent => Unit) :Dispatch[A] = 
    case (State(Seq(), pending, solid, jobs), Found(value)) =>
      State(Seq(), pending + value, solid, jobs)

    case (State(metrics, pending, solid, jobs), Found(value)) =>
      val (head, tail) = pending + value
      val job = fire(head, metrics)
      State(Seq(), tail, solid, jobs + (value.id -> job))
    
    case (State(metrics, pending, solid, jobs), NoMoreDevice) =>
      val actives = jobs.filter(_.isActive)

      if pending.isEmpty && actives.isEmpty then
        send(Unavailable)

      State(metrics, pending, true, actives)
    
    case (State(_, pending, solid, jobs), Reply(Unsupported(value, part, metrics))) =>
      val actives = (if part then jobs else jobs - value.name).filter(_.isActive)

      if pending.nonEmpty then
        val (head, tail) = pending
        val job = fire(head, metrics)
        State(Seq(), tail, solid, actives + (value.id -> job))
      else
        if solid && actives.isEmpty then send(Unavailable)
        State(metrics, pending, solid, actives)

    case (State(metrics, pending, solid, jobs), Reply(Notify(value, meter))) =>
      send(Available(value.name, meter))
      State(metrics, pending, solid, jobs)

    case (State(metrics, pending, solid, jobs), Reply(Fatal(value, cause))) =>
      // TODO log warning cause for debug
      
      val actives = (jobs - value.id).filter(_.isActive)
      if solid && actives.isEmpty then send(Unavailable)

      State(metrics, pending, solid, actives)

    case _ => ??? // TODO complain by unexpected state with event  

  case class State[A](
    metrics: Seq[BleMetrics]
    pending: Seq[A], 
    solid: Boolean,
    jobs: Map[String, Job]
  )

  enum Event[A]:
    case Found(value: A)
    case NoMoreDevice
    case Reply(event: BleConnectEvent)

  trait BleInfo[A]:
    extension (a: A)
      def id: String
      def name: String 
}

```