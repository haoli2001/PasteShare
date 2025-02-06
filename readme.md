# 简介
本项目为局域网内跨平台进行剪切板共享的工具，支持Windows、MacOS平台。(Linux平台未测试)
# 配置
## windows端配置
windows需要配置能够接受udp广播并开放12345端口
```powershell
netsh interface ipv4 set global multicastforwarding=enabled
```
## MacOS端配置
MacOS端建议直接关闭防火墙使用
## TODO
### 支持类型新增
当前仅支持文本类型，后续支持图片、文件等类型
### 支持字节长度限制
由于udp广播包大小的限制，当前仅取前60000字节，后续支持更大字节长度(进行分包处理)