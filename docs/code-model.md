## Code Model

```scala
type BleScan[D]    = Seq[BleMetric] => Flow[D]
type BleConnect[D] = Seq[BleMetric] => D => Flow[BleConnectEvent[D]]
type BleGather     = Seq[BleMetric] => Flow[BleEvent] 

enum BleConnectEvent[D]:
  case Unsupported(device: D, part: Boolean, metrics: Seq[BleMetric]) // part 为 true 表示部分不支持
  case Notify(device: D, meter: BleMeter)
  case Fatal(device: D, cause: String)

enum BleEvent:
  case Available(device: String, meter: BleMeter)
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
    case Available(device, (HeartRate, data)) => 
      // bleStatus = device
      // heartRate = read(data)
    case Available(device, (RunSpeedCadence, data)) => 
      // bleStatus = device
      // (pace, cadence) = read(data)
    case Unavailable              => 
      // bleStatus = "Tap to Rescan"
      // (heartRate, pace, cadence) => (null, null, null)
}

object BleViewModel {
  type Dispatch[D] = (State[D], Event[D]) => State[D]
  type ToConnect[D] = (D, Seq[BleMetric]) => Job

  def gather[D](
    scope: CoroutineScope, 
    scan: BleScan[D], 
    connect: BleConnect[D]
  ): BleGather = metrics => 
    val bus: Channel[Event] = ???
    
    val fire: ToConnect = (device, metrics) =>
      connect(device, metrics)
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


  def dispatch[D](fire: ToConnect[D], send: BleEvent => Unit) :Dispatch[D] = 
    case (State(Seq(), pending, solid, jobs), Found(device)) =>
      State(Seq(), pending + device, solid, jobs)

    case (State(metrics, pending, solid, jobs), Found(device)) =>
      val (head, tail) = pending + device
      val job = fire(head, metrics)
      State(Seq(), tail, solid, jobs + (device.id -> job))
    
    case (State(metrics, pending, solid, jobs), NoMoreDevice) =>
      val actives = jobs.filter(_.isActive)

      if pending.isEmpty && actives.isEmpty then
        send(Unavailable)

      State(metrics, pending, true, actives)
    
    case (State(_, pending, solid, jobs), Reply(Unsupported(device, part, metrics))) =>
      val actives = (if part then jobs else jobs - device.name).filter(_.isActive)

      if pending.nonEmpty then
        val (head, tail) = pending
        val job = fire(head, metrics)
        State(Seq(), tail, solid, actives + (device.id -> job))
      else
        if solid && actives.isEmpty then send(Unavailable)
        State(metrics, pending, solid, actives)

    case (State(metrics, pending, solid, jobs), Reply(Notify(device, meter))) =>
      send(Available(device.name, meter))
      State(metrics, pending, solid, jobs)

    case (State(metrics, pending, solid, jobs), Reply(Fatal(device, cause))) =>
      // TODO log warning cause for debug
      
      val actives = (jobs - device.id).filter(_.isActive)
      if solid && actives.isEmpty then send(Unavailable)

      State(metrics, pending, solid, actives)

    case _ => ??? // TODO complain by unexpected state with event  

  case class State[D](
    metrics: Seq[BleMetrics]
    pending: Seq[D], 
    solid: Boolean,
    jobs: Map[String, Job]
  )

  enum Event[D]:
    case Found(device: D)
    case NoMoreDevice
    case Reply(event: BleConnectEvent)
}

```