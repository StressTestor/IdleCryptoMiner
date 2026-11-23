import XCTest
@testable import IdleCryptoMiner

final class EconomyTests: XCTestCase {
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
