db = connect("mongodb://localhost:27017/chat_memory_db");
if (!db.topics.findOne({name: "计算机网络"})) {
    db.topics.insertOne({name: "计算机网络", docCount: 1, _class: "com.it.ai.aiagent.bean.Topic"});
}
