# Model

以下内容采用 Scala 代码表达的领域模型。在转换成对应 Kotlin 代码时，需要满足：

- 每个二级标题的内容应对应一个 `Xxx.kt` 文件，如 `Meter.kt`
- 函数类型 `type F = A => B` ，应转为 `fun interface F { operator fun invoke(a: A): B }`
- 函数类型 `type CreateF = ... => F`，应转为 `fun interface F` 的 `companion object` 的工厂方法

## Meter

```scala
type Meter[S] = (source: S, metric: Metric, data: Byte[])  
type Metric = (service: UUID, characteristic: UUID)
```

- `Meter` 是本领域的核心 **实体** 模型，它聚合了另外三个关键实体：
  - 数据的来源 `S` （考虑到来源类型细节不是重点，这里做了泛化处理，后同）
  - 数据的类别 `Metric`
  - 数据的本身 `Byte[]`
- `Metric` 则是对蓝牙领域中 Service 和 Characteristic 的封装模型


## Find

```scala
type Find[S] = (Set[Metric]) => Flow[Status[S]]
enum Status[S]:
  case Found(source: S)
  case Done(cause: Option[Throwable])
```

- `Find` 表示找出满足给定 `Set[Metric]` 的数据来源
- `Flow[Status[S]]` 表示查找过程是异步的
- `Done` 表示查找过程结束

## Connect

```scala
type Connect[S] = (S, Set[Metric]) => Flow[Status[S]]
enum Status[S]:
  case Unsupported(metrics: Set[Metric])
  case Received(meter: Meter[S])
  case Disconnected(source: S, cause: Option[Throwable])
```

- `Connect` 表示连接数据来源，在其支持众多的类别中，仅获取给定 `Set[Metric]` 的数据
- 连接的过程中会发现对于给定的 `Set[Metric]`， 数据来源可能不支持部分或全部，即 `Unsupported(metrics: Set[Metric])`
- 而对于支持的，则在连接的后续中会 `Received(meter: Meter[S])`
- `Disconnected(source, None)` 表示对于给定的 `Set[Metric]` 全部不支持
- `Disconnected(source, Some(cause))` 表示出现异常

## Gather

```scala
type Gather[S] = Set[Metric] => Flow[Meter[S]]
type CreateGather[S] = (Find[S], Connect[S]) => Gather[S]
```

- `Gather` 是本领域的核心 **行为** 模型，要实现对应 `Set[Metric]` 的 `Meter` 的采集过程，需要
  - 显性地依赖 `Find` 和 `Connect` 
  - 隐性地依赖 `React`

## React 

由于 `Find` 和 `Connect` 产生的异步状态事件流，因而设计了 `React` 来处理这些事件的，并更新对应的 `State[S]`：

- `unsupported` 最初就是要 `Gather` 的 `Set[Metric]`，后续
  - 在 `LaunchConnect` 后被清空
  - 在 `Connect.Status.Unsupported` 时，被更新
- `pending` 按顺序保存已找到，但有等待连接的来源，
  - 一旦 `unsupported` 不为 `empty` 时，则可以按先后顺序对第一个 `LaunchConnect`
- `connectings` 表示管理的活跃连接，即
  - 在 `LaunchConnect` 后，添加
  - 当 `Connect.Status.Disconnected` 时， 减少
  - 配合 `pending` 和 `noMoreSource` 来决定，是否要 `SendChannel.close`
  - 在 `Flow[Meter[S]]` 消费端关闭时，能被用来 `Cancel`正在连接，避免泄漏
- `noMoreSource` 记录是否收到 `Find.Status.Done`


```scala
type React[S] = (State[S], Event[S]) => State[S]
type CreateReact[S] = (SendChannel[Meter[S]], LaunchConnect[S]) => React[S]

type Event[S] = Find.Status[S] | Connect.Status[S]

type State[S] = (
  unsupported: Set[Metric],
  pending: Queue[S] = empty(), 
  connectings: Map[S, Cancel] = empty(), 
  noMoreSource: Boolean = false
)  

type LaunchConnect[S] = (S, Set[Metric]) => Cancel
type Cancel = () => Unit
```
