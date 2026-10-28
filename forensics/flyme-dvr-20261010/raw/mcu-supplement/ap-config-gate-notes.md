# AP配置门槛检查边界

未知：DVRConfig是否阻止AP发出D8请求。只查看此前未作为配置门槛分析的generic onSetProperty入口；已有D8表映射/封包结果继续沿用。

EmulatedVehicleConnector::onSetProperty(c1364)先做EmulatedUserHal属性判断，普通属性走VehicleHalServer::onSetProperty(113348)，后者含通用属性处理、回调及存储分支。当前未恢复从Java car_config cmd102/DVRConfig到这些回调、再到D8发送的配置数据流；这两个函数的局部反汇编不足以证明没有配置门控。

结果：本检查没有得到可用于启用的枚举，也未取得新的DVR功能证据，不计为实际使用进展。不将未发现配置比较写成配置不起作用。原指令保存ap-config-gate.txt。

重查条件：识别具体D8发送回调的配置读取来源、配置对象写入者，或出现同版本匹配源码/配置消费者。没有新数据流线索时不重复generic入口扫描。
