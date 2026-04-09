## Code Model

```scala
type BleScan[D]    = List[BleMetric] => Flow[D]
type BleConnect[D] = List[BleMetric] => D => Flow[BleConnectEvent[D]]
type BleGather     = List[BleMetric] => Flow[BleEvent] 

enum BleConnectEvent[D]:
  case Unsupported(metrics: List[BleMetric])
  case Notify(device: D, meter: BleMeter)
  case Fatal(device: D, cause: String)

enum BleEvent:
  case Scanning
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
) extends ViewModel:
  val bleStatus: StateFlow[String] = ??? //
  val heartRate: StateFlow[Option[String]] = ??? // None as Default
  val pace: StateFlow[Option[String]] = ??? // None as Default
  val cadence: StateFlow[Option[String]] = ??? // None as Default

  def run:
    gather(viewModelScope, scan, connect)(List(HearRate, RunSpeedCadence))
      .onEach(handle)
      .launchInScope(viewModelScope)

  def handle: PartialFunction[BleEvent, Unit] = 
    case Scanning                 => ???
    case Available(device, meter) => ???
    case Unavailable              => ???

object BleViewModel:
  def gather[D](scope: CoroutineScope, scan: BleScan[D], connect: BleConnect[D]): BleGather = metrics =>

```