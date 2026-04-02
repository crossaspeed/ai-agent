序列化器：

作用：Java对象转换为JSON格式，JSON格式转换为Java对象

使用方法：

如果是SpringBoot项目，只需要导入依赖：

```yaml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

```java
ObjectMapper objectMapper = new ObjectMapper();
// task是一个Java对象
// Java对象->JSON字符串
Task task = new Task();
String jsonString = objectMapper.writeValueAsString(task);
// 将JSON字符串->Java对象
String jsonInput = "一段json字符串";
Task task = objectMapper.readValue(jsonInput, Task.class);
// 处理JSON数组
// Java泛型擦除：泛型（<...>）只在编译期存在。一旦程序运行起来（运行期），所有的泛型信息都会被抹除
// TypeReference 是 Jackson 提供的一个非常重要的工具类，它的主要作用是解决 Java 泛型擦除
List<Task> list = objectMapper.readValue(jsonList, newTypeReference<List<Task>>() {});
```



Optional的使用：

作用：理解成一个盒子 盒子里面有值，那么久查询到了任务 盒子里面没有值 那么久没有查询到任务

适用范围：mapper层查询数据的时候，返回一个对象？因为某种原因，可能查询不到值。

用法：java.util包

```java
Optional<Task> taskOptional = mapper.findById(id);
```

