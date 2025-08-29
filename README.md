# Conductor - 命令模式完整實作範例

## 專案概述

Conductor是一個完整的Java 17 + Spring Boot 3 + Maven專案，展示了命令模式(Command Pattern)的初階、進階、高階應用。

## 技術架構

- **Java**: 17
- **Spring Boot**: 3.1.5
- **Maven**: 構建工具
- **H2 Database**: 記憶體資料庫
- **Jackson**: JSON處理
- **JPA/Hibernate**: 資料持久化

## 專案結構

```
com.ryan.demo.conductor/
├── basic/                           # 初階應用 - 基本命令模式
│   ├── Command.java                 # 命令介面
│   ├── CommandResult.java           # 命令結果
│   ├── TextReceiver.java            # 文字接收者
│   ├── WriteCommand.java            # 寫入命令
│   ├── DeleteCommand.java           # 刪除命令
│   ├── ClearCommand.java            # 清空命令
│   └── CommandInvoker.java          # 命令調用者
├── advanced/                        # 進階應用 - 命令佇列和持久化
│   ├── MacroCommand.java            # 巨集命令
│   ├── CommandQueue.java            # 命令佇列
│   ├── CommandPersistence.java      # 命令持久化
│   └── eventsourcing/              # 高階應用 - 事件溯源
│       ├── EventSourcingCommand.java     # 事件溯源命令基類
│       ├── UserAggregate.java            # 使用者聚合根
│       ├── CreateUserCommand.java        # 建立使用者命令
│       ├── UpdateUserCommand.java        # 更新使用者命令
│       └── InMemoryEventStore.java       # 記憶體事件庫
├── controller/                      # REST API
│   └── CommandDemoController.java   # 演示控制器
└── ConductorApplication.java       # 主程式
```

## 命令模式應用層級

### 初階應用 - 基本命令模式
- **基本命令封裝**: 將請求封裝為物件
- **撤銷/重做功能**: 支援操作的撤銷和重做
- **命令歷史管理**: 維護命令執行歷史
- **命令監聽器**: 支援命令執行的監聽和回調

### 進階應用 - 命令佇列和持久化
- **巨集命令**: 組合多個命令為一個複合操作
- **非同步命令佇列**: 支援非同步命令執行
- **命令優先級**: 支援不同優先級的命令處理
- **命令持久化**: 將命令執行記錄持久化存儲
- **批次處理**: 支援命令的批次提交和執行

### 高階應用 - 事件溯源模式
- **事件溯源**: 通過事件流重建聚合狀態
- **聚合根模式**: DDD中的聚合根實作
- **並發控制**: 樂觀鎖和版本控制
- **補償機制**: 通過補償事件實現撤銷
- **CQRS模式**: 命令查詢責任分離

## 快速開始

### 1. 編譯專案
```bash
mvn clean compile
```

### 2. 運行測試
```bash
mvn test
```

### 3. 啟動應用程式
```bash
mvn spring-boot:run
```

### 4. 訪問API
應用程式啟動後，可通過以下URL訪問：

- **基本命令演示**: `http://localhost:8080/api/command-demo/basic/`
- **進階命令演示**: `http://localhost:8080/api/command-demo/advanced/`
- **事件溯源演示**: `http://localhost:8080/api/command-demo/advanced/eventsourcing/`
- **綜合工作流程**: `http://localhost:8080/api/command-demo/comprehensive/workflow`

## API端點說明

### 基本命令API
```http
POST   /api/command-demo/basic/write        # 執行寫入命令
POST   /api/command-demo/basic/delete       # 執行刪除命令
POST   /api/command-demo/basic/clear        # 執行清空命令
POST   /api/command-demo/basic/undo         # 撤銷上一個命令
POST   /api/command-demo/basic/redo         # 重做上一個命令
GET    /api/command-demo/basic/status       # 獲取基本狀態
```

### 進階命令API
```http
POST   /api/command-demo/advanced/macro            # 執行巨集命令
POST   /api/command-demo/advanced/queue/submit     # 提交命令到佇列
GET    /api/command-demo/advanced/queue/status     # 獲取佇列狀態
```

### 事件溯源API
```http
POST   /api/command-demo/advanced/eventsourcing/create-user    # 建立使用者
POST   /api/command-demo/advanced/eventsourcing/update-user/{userId}  # 更新使用者
GET    /api/command-demo/advanced/eventsourcing/user/{userId}  # 獲取使用者
GET    /api/command-demo/advanced/eventsourcing/statistics     # 獲取統計資訊
```

### 綜合演示API
```http
POST   /api/command-demo/comprehensive/workflow    # 執行複雜工作流程
POST   /api/command-demo/reset                     # 重置演示環境
```

## 設計模式優勢展示

### 1. 請求封裝
- 將請求封裝為物件，支援參數化和佇列化
- 解耦調用者和接收者

### 2. 撤銷和重做
- 支援操作的撤銷和重做
- 維護操作歷史記錄

### 3. 巨集命令
- 組合多個命令為複合操作
- 支援事務性操作

### 4. 非同步處理
- 命令佇列支援非同步執行
- 提高系統響應性和吞吐量

### 5. 事件溯源
- 通過事件流完整記錄狀態變化
- 支援時間旅行和審計追蹤

## 測試覆蓋

### 單元測試
- `BasicCommandTest`: 測試基本命令功能
- `AdvancedCommandTest`: 測試進階命令功能  
- `EventSourcingTest`: 測試事件溯源功能

### 運行測試
```bash
# 運行所有測試
mvn test

# 運行特定測試類別
mvn test -Dtest=BasicCommandTest
mvn test -Dtest=EventSourcingTest

# 運行測試並生成報告
mvn test -Dmaven.test.failure.ignore=true
```

## 核心特性

### 命令模式基礎
- **Command介面**: 統一的命令執行介面
- **ConcreteCommand**: 具體命令實作
- **Receiver**: 命令接收者和執行者
- **Invoker**: 命令調用者和管理者

### 進階特性
- **巨集命令**: 組合模式的應用
- **命令佇列**: 生產者-消費者模式
- **命令持久化**: 持久化和統計
- **優先級處理**: 支援不同優先級

### 高階特性
- **事件溯源**: 完整的事件驅動架構
- **聚合根**: DDD聚合根模式
- **並發控制**: 樂觀鎖機制
- **CQRS**: 命令查詢分離

## 實際應用場景

### 文字編輯器
- 文字操作命令
- 撤銷/重做功能
- 巨集錄製和回放

### 工作流引擎
- 工作流步驟封裝
- 流程回滾和補償
- 分散式任務調度

### 金融交易系統
- 交易命令處理
- 事件溯源和審計
- 系統狀態重建

### 遊戲開發
- 玩家動作命令
- 遊戲狀態管理
- 回放和重現

## 性能考量

### 記憶體管理
- 命令歷史大小限制
- 事件快照機制
- 垃圾回收優化

### 並發處理
- 線程安全的命令佇列
- 樂觀鎖並發控制
- 非阻塞操作設計

### 持久化優化
- 批次寫入機制
- 異步持久化
- 索引和查詢優化

## 擴展建議

### 分散式支援
- 分散式命令佇列
- 事件總線整合
- 微服務命令路由

### 監控和觀測
- 命令執行監控
- 性能指標收集
- 分散式追蹤

### 安全和權限
- 命令權限控制
- 審計日誌加密
- 身份驗證整合

## 貢獻指南

1. Fork專案
2. 建立功能分支
3. 編寫測試
4. 提交Pull Request

## 授權

MIT License

---

**作者**: Ryan  
**版本**: 1.0.0  
**最後更新**: 2025-08-29