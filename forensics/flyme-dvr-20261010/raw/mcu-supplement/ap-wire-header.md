# AP串口报文头缺口复核

未知：历史body简述未列出的字节是否使MCU拆包错位，单字节D8是否被长度检查拒绝。本次因明确偏移证据缺口复核既有AP文件，未重取或写车辆。

AP McuManager::sendMessage 1895e4：189a68取getCmdCtrl写offset2；189aa0取getBodyLength加3写offset3；189ae0取main写offset4；189b18取sub写offset5；189b50取getSnPrio写offset6；189b8c起附加body；189bb4计算并附加checksum。header为55 aa ctrl length main sub snPrio，body随后开始。

McuCanMessage::Builder::build 1da5c0的body是route、count、各条目[len+2, modelHi, modelLo, payload]，body长度=2+sum(payloadlen+3)。因此原包offset7=route，offset8=count，offset9=entrylen，offset10/11=modelID，offset12开始payload，与MCU45a8e8/439984一致。单条1字节payload的body长6、线格式length字节9，满足MCUlength>=9。length字节不是整包总字节数，整包还含前缀及checksum。

结果：补全历史简述，消除错位疑点；没有生成可执行控制报文，没有证明snPrio任意取值、Enter/退出语义或对端在线。

证据：ap-can-builder-recheck.txt、ap-message-wire-recheck.txt；getSnPrio=Message对象+12见18f70c，构造器18f650写入w4。重查条件：AP文件/MCU版本变化，或发现另一发送入口改变头部。相同版本不重复复核。
