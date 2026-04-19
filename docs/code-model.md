## Code Model

```scala
package hanabix.hubu.ble

enum BleMetric(val service: UUID, val characteristic: UUID) {
  case HeartRate extends BleMetric(???, ???)
  case RunSpeedCadence extends BleMetric(???, ???)
}

case class BleMeter(metric: BleMetric, data: Byte[])

type BleGather = Seq[BleMetric] => Flow[BleEvent]
object BleGather {
  def apply[A](
    scan: BleScan[A], 
    scanTimeout: Duration,
    connect：BleConnect[A],
    react: BleReact[A]
  ): BleGather = ???
}
  
enum BleEvent {
  case Available(deviceName: String, meter: BleMeter)
  case Unavailable
}

type Launch[A] = (A, Seq[BleMetric]) => Job

trait BleChannel[A] {
  def emit(a: A): Unit
  def close(): Unit
}

type BleReact[A] = (State[A], Event[A], BleChannel[BleEvent], Launch[A]) => State[A]
object BleReact {

  def apply[A](info: DeviceInfo[A]): BleReact[A] = ???
  
  case class State[A](
    unsupported: Seq[BleMetric],
    scanningEnded: Boolean = false,
    pending: Seq[A] = Seq.empty(),
    connecting: Map[DeviceId, Job] = Map.empty()  
  ) {
    def unsupported(metrics: Seq[BleMetric]): State[A] = ???
    def scanningEnded(): State[A] = ???
    def pending(fn: Seq[A] => Seq[A]): State[A] = ???
    def connecting(fn: Map[DeviceId, Job] => Map[DeviceId, Job]): State[A] = ???
    def launchPending(info: DeviceInfo[A], launch: Launch[A]): State[A] = ???
    def sendIfUnavailable(channel: BleChannel[BleEvent]): State[A] = ???
  }
  
  enum Event[A]:
    case Found(device: A)
    case ScanningEnded
    case Connecting(event: BleConnect.Event)
    
  trait DeviceInfo[A]:
    extension (a: A)
      def id: String
      def name: String   
}

type BleScan[A] = Seq[BleMetric] => Flow[A]
object BleScan {
  type CreateCallback = BleChannel[ScannedDevice] => ScanCallback 
  def apply(context: Context, createCallback: CreateCallback): BleScan[ScannedDevice] = ???
  
  def default: CreateCallback = ???
}
  
type BleConnect[A] = (Seq[BleMetric], A) => Flow[BleConnect.Event[A]]
object BleConnect {
  enum Event[A]:
    case Connected(unsupported: Seq[BleMetric])
    case Abandon(device: A, unsupported: Seq[BleMetric])
    case Notify(device: A, meter: BleMeter)
    case Disconnected(device: A)
  
  type CreateCallback = (ScannedDevice, Seq[BleMetric], BleChannel[Event]) => BluetoothGattCallback
  
  def apply(context: Context, createCallback: CreateCallback): BleConnect[ScannedDevice] = ???
  
  def default: CreateCallback = ???
}

case class ScannedDevice(name: String, target: BluetoothDevice)
```
