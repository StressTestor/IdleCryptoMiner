import XCTest

final class GameUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    private func launch(seed: Bool = false) {
        app.launchArguments = ["--uitesting", "--fresh-save"] + (seed ? ["--seed-save"] : [])
        app.launch()
    }

    private func reveal(_ element: XCUIElement) {
        for _ in 0..<8 {
            if element.isHittable { return }
            app.swipeUp()
        }
        XCTAssertTrue(element.isHittable)
    }

    func testPrompt() {
        launch()
        XCTAssertEqual(app.staticTexts["tokenBalance"].label, "0 TOKENS")
        app.buttons["prompt"].tap()
        XCTAssertEqual(app.staticTexts["tokenBalance"].label, "1 TOKENS")
    }

    func testBuyModelAndOfflineRelaunch() {
        launch(seed: true)
        let row = app.buttons["model.gpu1"]
        reveal(row)
        row.tap()
        XCTAssertTrue(row.label.contains("owned 1"))
        app.swipeDown()
        XCTAssertEqual(app.staticTexts["productionRate"].label, "+1 Tokens/sec")
        app.terminate()
        app.launchArguments = ["--uitesting", "--offline-save"]
        app.launch()
        XCTAssertTrue(app.alerts["OFFLINE EARNINGS"].waitForExistence(timeout: 5))
        let message = app.alerts.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Tokens while offline'")).firstMatch
        XCTAssertTrue(message.exists)
        let earned = Int(message.label.split(separator: " ")[2]) ?? 0
        XCTAssertGreaterThanOrEqual(earned, 180)
        XCTAssertLessThan(earned, 200)
        app.buttons["COLLECT"].tap()
    }

    func testHypeCycleCountdown() {
        launch()
        let boost = app.buttons["hypeCycle"]
        boost.tap()
        XCTAssertTrue(boost.label.hasPrefix("HYPE CYCLE ACTIVE:"))
        let initial = boost.label
        expectation(for: NSPredicate(format: "label != %@", initial), evaluatedWith: boost)
        waitForExpectations(timeout: 5)
        XCTAssertFalse(boost.isEnabled)
    }

    func testFundingRound() {
        launch(seed: true)
        app.buttons["fundingRound"].tap()
        app.alerts.buttons["PIVOT TO AGI"].tap()
        XCTAssertEqual(app.staticTexts["tokenBalance"].label, "0 TOKENS")
        XCTAssertEqual(app.staticTexts["vcBillions"].label, "1 VC Billions")
        XCTAssertEqual(app.staticTexts["productionRate"].label, "+0 Tokens/sec")
        XCTAssertFalse(app.buttons["fundingRound"].isEnabled)
    }

    func testResetProgress() {
        launch(seed: true)
        let row = app.buttons["model.gpu1"]
        reveal(row)
        row.tap()
        let settings = app.buttons["settings"]
        reveal(settings)
        settings.tap()
        app.buttons["resetProgress"].tap()
        app.alerts.buttons["Reset"].tap()
        for _ in 0..<6 { app.swipeDown() }
        XCTAssertEqual(app.staticTexts["tokenBalance"].label, "0 TOKENS")
        XCTAssertEqual(app.staticTexts["productionRate"].label, "+0 Tokens/sec")
        XCTAssertEqual(app.staticTexts["vcBillions"].label, "0 VC Billions")
        reveal(row)
        XCTAssertTrue(row.label.contains("owned 0"))
    }
}
