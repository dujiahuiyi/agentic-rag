# agentic-rag

`agentic-rag` 是一个基于 `Spring Boot + LangChain4j` 的知识问答后端项目，围绕私有文档入库、检索增强、异步处理和受控联网兜底构建。

项目当前重点包括：

- 文档上传后异步切片、向量化与状态追踪
- 基于 `Milvus` 的语义召回
- 基于 `Elasticsearch` 的关键词召回
- 本地双路召回结果的合并与评估
- 仅在本地结果不足时才启用的外部搜索兜底

## 功能概览

### 文档上传与异步处理

- 上传接口接收文档并立即返回任务 ID
- 文档元数据先写入 MySQL
- 通过 `RabbitMQ` 异步触发切片、向量化和索引流程
- 消费端使用状态抢占与幂等判断，避免重复消费导致重复处理

### 文档切片

当前已支持面向 `txt/md` 的基础结构化切片：

- 标题优先
- 段落优先
- 超长文本递归切片

切片结果会被分批处理，降低 embedding 与写库时的大批量失败风险。

### 本地检索

项目当前的本地检索由两部分组成：

- `Milvus`：负责语义向量召回
- `Elasticsearch`：负责关键词召回

在此基础上，项目实现了一个轻量的本地混合召回器：

- 双路结果合并
- 以 `(doc_id, chunk_index)` 去重
- 保留召回来源信息，便于后续评估与调试

### 受控外部搜索

项目接入了 `Tavily` 作为外部搜索源，但外部搜索不会直接参与本地知识候选池，而是作为受控兜底使用：

- 内部问题优先使用本地知识
- 非内部问题在本地召回较弱时，才会启用外部搜索

这可以尽量避免外部通用知识覆盖内部文档结论。

## 核心链路

### 文档处理链路

`上传文档 -> MySQL 写任务 -> afterCommit 发 MQ -> RabbitMQ 消费 -> 切片 -> 分批 embedding -> 写入 Milvus 与 Elasticsearch -> 更新任务状态`

### 问答链路

`用户提问 -> 本地双路召回(Milvus + Elasticsearch) -> 本地召回强弱评估 -> 结果足够则直接回答 / 结果不足则外部搜索兜底 -> 大模型生成最终回复`

## 技术栈

- Java 17
- Spring Boot 3
- MyBatis-Plus
- MySQL
- Redis
- RabbitMQ
- Milvus
- Elasticsearch
- LangChain4j
- Tavily

## 当前实现特点

- 文档处理链路异步化，接口响应更快
- 生产端采用事务提交后发 MQ，降低数据库和消息发送不一致风险
- 消费端具备幂等处理和失败清理能力
- 本地检索不再依赖单一路径，支持语义检索与关键词检索协同工作
- 外部搜索通过门禁控制，不直接污染本地知识结果

## 项目结构建议理解

- `controllers`
  - 对外接口入口
- `service`
  - 业务逻辑与检索评估逻辑
- `consumers`
  - RabbitMQ 消费处理
- `configs`
  - AI、MQ、ES 等基础配置
- `domain`
  - 请求、响应、检索中间对象与部分检索器实现

## 运行依赖

项目运行前需要准备：

- MySQL
- Redis
- RabbitMQ
- Milvus
- Elasticsearch
- Tavily API Key
- 大模型 / Embedding 模型 API Key

配置项请参考：

- `src/main/resources/application.yml.example`

## 后续可继续优化的方向

- 为 `pdf/doc/docx` 引入更强的结构化切片
- 增加本地双路召回之后的真正重排流程
- 优化本地召回强弱判断策略
- 增加更多集成测试与端到端验证
- 根据业务类型细化内部问题识别规则
