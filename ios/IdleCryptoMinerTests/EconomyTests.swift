import XCTest
@testable import IdleCryptoMiner

final class EconomyTests: XCTestCase {
    func testBulkMilestonesAndPreview() {
        let model = OwnedHardware(tier: GameRules.hardware[0], count: 0)
        XCTAssertEqual(model.bulkCost(0), 0)
        XCTAssertEqual(model.bulkCost(2), d("21.5"))
        XCTAssertEqual(model.maxAffordable(9), 0)
        for n in [1, 10, 100, 1000] {
            let cost = model.bulkCost(n)
            XCTAssertEqual(model.maxAffordable(cost), n)
            XCTAssertEqual(model.maxAffordable(cost * d("0.999999")), n - 1)
        }
        let huge = d("1e120")
        let n = model.maxAffordable(huge)
        XCTAssertGreaterThan(n, 100)
        XCTAssertLessThanOrEqual(model.bulkCost(n), huge)
        XCTAssertTrue(model.bulkCost(n + 1).isNaN || model.bulkCost(n + 1) > huge)
        for (count, multiplier) in [(0, 1), (10, 2), (25, 4), (50, 8), (100, 24), (200, 72), (500, 1944)] {
            XCTAssertEqual(milestoneMultiplier(count), Decimal(multiplier))
            let progress = milestoneProgress(count)
            XCTAssertLessThanOrEqual(progress.previous, count)
            XCTAssertGreaterThan(progress.next, count)
        }
        XCTAssertEqual(milestoneProgress(12).next, 25)
        XCTAssertEqual(nextPrestigeThreshold(1000000), 4000000)
        XCTAssertEqual(prestigePreview(1000000), "Pivot now: +1 VC Billions (+10%)\nNext VC Billion in 3.00M run Tokens")
        XCTAssertEqual(prestigePreview(0), "1.00M run Tokens to your first VC Billion")
        XCTAssertEqual(prestigePreview(999999), "1 run Tokens to your first VC Billion")
    }

    @MainActor
    func testOldFormatSave() {
        let suite = "EconomyTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let json = """
        {"version":1,"hash":123,"counts":{"cpu":12},"runEarned":456,"prestigeCores":2,"boostEnd":0,"lastSave":0,"effectsMuted":true}
        """
        defaults.set(Data(json.utf8), forKey: "idleCryptoMiner.save.v1")
        let store = GameStore(defaults: defaults)
        XCTAssertEqual(store.counts["cpu"], 12)
        XCTAssertEqual(store.prestigeCores, 2)
    }

    func testCostGrowth() {
        let tier = GameRules.hardware[0]
        XCTAssertEqual(OwnedHardware(tier: tier, count: 0).currentCost, 10)
        XCTAssertEqual(OwnedHardware(tier: tier, count: 2).currentCost, d("13.225"))
    }

    func testAccrueWithBoost() {
        XCTAssertEqual(accrue(rate: 10, seconds: 10, boostedSeconds: 3), 130)
        XCTAssertEqual(accrue(rate: 10, seconds: 10, boostedSeconds: 30), 200)
        XCTAssertEqual(accrue(rate: 10, seconds: 0, boostedSeconds: 3), 0)
        XCTAssertEqual(accrue(rate: -1, seconds: 10, boostedSeconds: -3), 0)
    }

    func testPrestigeCores() {
        XCTAssertEqual(prestigeCoresFor(-1), 0)
        XCTAssertEqual(prestigeCoresFor(999999), 0)
        XCTAssertEqual(prestigeCoresFor(1000000), 1)
        XCTAssertEqual(prestigeCoresFor(3999999), 1)
        XCTAssertEqual(prestigeCoresFor(4000000), 2)
        XCTAssertEqual(prestigeCoresFor(d("1e50")), Int.max)
    }

    @MainActor
    func testOfflineCapAndInvalidSave() {
        let suite = "EconomyTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        defaults.set(Data("invalid".utf8), forKey: "idleCryptoMiner.save.v1")
        let store = GameStore(defaults: defaults)
        XCTAssertEqual(store.hash, 0)
        for _ in 0..<10 { store.manualMine() }
        XCTAssertTrue(store.buy(store.ownedHardware[0]))
        store.save(at: Date().addingTimeInterval(-100000))
        let restored = GameStore(defaults: defaults)
        XCTAssertEqual(restored.hash, d("2880"))
        XCTAssertEqual(restored.offlineEarnings, d("2880"))
        restored.save(at: Date().addingTimeInterval(1000))
        XCTAssertEqual(GameStore(defaults: defaults).hash, restored.hash)
    }

    func testFormatHash() {
        XCTAssertEqual(formatHash(0), "0")
        XCTAssertEqual(formatHash(-1), "0")
        XCTAssertEqual(formatHash(d("999.99")), "999")
        XCTAssertEqual(formatHash(1999), "1.99K")
        XCTAssertEqual(formatHash(d("1e33")), "1.00Dc")
        XCTAssertEqual(formatHash(d("1e40")), "1.00e40")
    }
}
