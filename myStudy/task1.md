# docker部署服务器的操作
编写dockerfile文件的内容
这里有两个阶段：源码编译和运行环境
源码编译：（核心作用：将java源代码编译成.jar包）
FROM 第一条非注释指令，决定了基础的运行环境
AS 指定别名 ：后面有一句 COPY --from=build ...，这里的 build 必须和前面的 AS 后面的名字对应
写法遵循：镜像名:版本号-操作系统/环境描述
详情参考dockerhub上面的指令
FROM maven:3.9.9-eclipse-temurin-17 AS build
设置容器内的工作目录为/app
WORKDIR /app

拷贝依赖文件跟源文件
COPY pom.xml ./
COPY src ./src

RUN mvn -DskipTests clean package org.springframework.boot:spring-boot-maven-plugin:3.2.6:repackage

运行环境：
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/ai-agent-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

容器启动的时候终极运行命令：
"java": 启动 Java 虚拟机（JVM）。
• "-Duser.timezone=Asia/Shanghai":
◦ 作用：设置时区。
◦ 原理：很多服务器默认是 UTC 时间（比北京时间慢 8 小时）。如果不加这个，你的程序日志、存入数据库的时间都会错乱。
• "-Xms256m", "-Xmx768m":
◦ 作用：JVM 内存调优。
◦ Xms 是初始内存，Xmx 是最大可用内存。
◦ 原理：如果不限制，Java 可能会疯狂占用内存导致宿主机崩溃。你设置的 768m 意味着这个程序最多只能吃掉服务器 768MB 的内存。
• "-XX:MaxMetaspaceSize=256m":
◦ 作用：限制元空间（存储类信息的地方）的大小，防止类加载过多导致内存溢出。
• "-jar", "/app/app.jar":
◦ 作用：告诉 Java 运行哪一个 Jar 包。这里的 /app/app.jar 正是你上一行代码从编译阶段拷贝过来的。
• "--spring.profiles.active=prod":
◦ 作用：这是 Spring Boot 专属参数。
◦ 原理：告诉你的代码：“现在是生产环境，请去读取 application-prod.yml 里的配置（如正式数据库地址）”。

ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-Xms256m", "-Xmx768m", "-XX:MaxMetaspaceSize=256m", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
