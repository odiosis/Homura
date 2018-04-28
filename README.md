# Homura
分布式实时聊天系统

## 目标
作为一个vertx的poc项目，验证vertx的性能。

## 使用
1. build project `mvn package`  

2. config 
    - maxfiles/openfiles
    - net.inet.ip.portrange

3. run
```bash
//run server
//服务端监听7777端口 如果没有graphite可以注掉响应代码
java -jar Homura*.jar

//run client
//客户端默认连接7777端口 发送消息的间隔默认1s
//args: ${ips}-如有多个ip用,分割 ${clients}-每个ip建立多少个client ${talksPerClient}-每个client发几条消息
java -cp Homura*.jar top.devgo.vertx.benchmark.LoadTest ${ips} ${clients} ${talksPerClient}
```

### 消息协议
见wiki
