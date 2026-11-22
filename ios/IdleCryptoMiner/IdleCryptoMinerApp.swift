import Foundation
import SwiftUI

#if canImport(UIKit)
import UIKit
#endif

@main
struct IdleCryptoMinerApp: App {
    var body: some Scene {
        WindowGroup {
            GameView()
        }
    }
}

struct HardwareTier: Identifiable, Hashable {
    let id: String
    let name: String
    let baseCost: Decimal
    let baseRate: Decimal
}

struct OwnedHardware: Identifiable, Hashable {
    let tier: HardwareTier
    let count: Int

    var id: String { tier.id }
    var name: String { tier.name }
    var baseRate: Decimal { tier.baseRate }

    var currentCost: Decimal {
        tier.baseCost * powDecimal(GameRules.costGrowth, count)
    }

    var currentRate: Decimal {
        tier.baseRate * Decimal(count) * milestoneMultiplier(count)
    }

    func bulkCost(_ quantity: Int) -> Decimal {
        guard quantity > 0 else { return 0 }
        let growth = powDecimal(GameRules.costGrowth, quantity)
        guard !growth.isNaN else { return .nan }
        return currentCost * (growth - 1) / (GameRules.costGrowth - 1)
    }

    func maxAffordable(_ balance: Decimal) -> Int {
        var low = 0
        let limit = Int.max - count
        var high = min(1, limit)
        func affordable(_ n: Int) -> Bool {
            let cost = bulkCost(n)
            return !cost.isNaN && cost <= balance
        }
        while high < limit && affordable(high) {
            low = high
            high = high > limit / 2 ? limit : high * 2
        }
        while low < high {
            let mid = low + (high - low) / 2 + (high - low) % 2
            if affordable(mid) { low = mid } else { high = mid - 1 }
        }
        return low
    }
}

enum GameRules {
    static let hardware: [HardwareTier] = [
        HardwareTier(id: "cpu", name: "ChatGTP-2", baseCost: d("10"), baseRate: d("0.1")),
        HardwareTier(id: "gpu1", name: "Lame-a 3 8B", baseCost: d("120"), baseRate: d("1.5")),
        HardwareTier(id: "gpu2", name: "Mistrial Medium", baseCost: d("1500"), baseRate: d("24")),
        HardwareTier(id: "asic", name: "Jeminai Ultra", baseCost: d("18000"), baseRate: d("360")),
        HardwareTier(id: "rig", name: "Clawd Opus", baseCost: d("220000"), baseRate: d("5500")),
        HardwareTier(id: "rack", name: "DeepSeekr R1", baseCost: d("2600000"), baseRate: d("83000")),
        HardwareTier(id: "datacenter", name: "Grock 4 Heavy", baseCost: d("32000000"), baseRate: d("1300000")),
        HardwareTier(id: "quantum", name: "GPT-∞ (Totally AGI)", baseCost: d("400000000"), baseRate: d("20000000"))
    ]

    static let costGrowth = d("1.15")
    static let tapFraction = d("0.10")
    static let prestigeThreshold = d("1000000")
    static let coreBonus = d("0.10")
    static let boostMultiplier = d("2")
    static let boostDuration: TimeInterval = 5 * 60
    static let boostCooldown: TimeInterval = 5 * 60
    static let offlineCap: TimeInterval = 8 * 60 * 60
    static let offlineDialogMin: TimeInterval = 60
    static let autoSaveEvery: TimeInterval = 20
}

private struct SaveGame: Codable {
    var version: Int
    var hash: Decimal
    var counts: [String: Int]
    var runEarned: Decimal
    var prestigeCores: Int
    var boostEnd: TimeInterval
    var lastSave: TimeInterval
    var effectsMuted: Bool
}

@MainActor
final class GameStore: ObservableObject {
    @Published private(set) var hash: Decimal = 0
    @Published private(set) var counts: [String: Int] = [:]
    @Published private(set) var runEarned: Decimal = 0
    @Published private(set) var prestigeCores: Int = 0
    @Published private(set) var boostEnd: Date = .distantPast
    @Published private(set) var offlineEarnings: Decimal = 0
    @Published private(set) var effectsMuted: Bool = false

    private let defaults: UserDefaults
    private let saveKey = "idleCryptoMiner.save.v1"
    private var lastTick = Date()
    private var lastAutoSave = Date()

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("--uitesting") {
            if arguments.contains("--fresh-save") { defaults.removeObject(forKey: saveKey) }
            if arguments.contains("--seed-save") {
                let seed = SaveGame(version: 1, hash: 1000000, counts: [:], runEarned: 1000000,
                                    prestigeCores: 0, boostEnd: 0, lastSave: Date().timeIntervalSince1970,
                                    effectsMuted: true)
                defaults.set(try? JSONEncoder().encode(seed), forKey: saveKey)
            }
            if arguments.contains("--offline-save"),
               let data = defaults.data(forKey: saveKey),
               var state = try? JSONDecoder().decode(SaveGame.self, from: data) {
                state.lastSave -= 120
                defaults.set(try? JSONEncoder().encode(state), forKey: saveKey)
            }
        }
        #endif
        load()
    }

    var ownedHardware: [OwnedHardware] {
        GameRules.hardware.map { tier in
            OwnedHardware(tier: tier, count: counts[tier.id, default: 0])
        }
    }

    var passiveRate: Decimal {
        ownedHardware.reduce(Decimal.zero) { partial, hardware in
            partial + hardware.currentRate
        }
    }

    var prestigeMultiplier: Decimal {
        Decimal(1) + (Decimal(prestigeCores) * GameRules.coreBonus)
    }

    var effectiveRate: Decimal {
        passiveRate * prestigeMultiplier
    }

    var pendingCores: Int {
        prestigeCoresFor(runEarned)
    }

    func tick(at now: Date = Date()) {
        let elapsed = max(0, Int(now.timeIntervalSince(lastTick)))
        guard elapsed > 0 else { return }

        let end = lastTick.addingTimeInterval(TimeInterval(elapsed))
        let boosted = boostedSeconds(from: lastTick, to: end, boostEnd: boostEnd)
        let gained = accrue(rate: effectiveRate, seconds: elapsed, boostedSeconds: boosted)
        addHash(gained)
        lastTick = end

        if now.timeIntervalSince(lastAutoSave) >= GameRules.autoSaveEvery {
            save(at: now)
            lastAutoSave = now
        }
    }

    func manualMine(at now: Date = Date()) {
        let passiveTap = passiveRate * GameRules.tapFraction
        var amount = passiveTap > 1 ? passiveTap : 1
        amount *= prestigeMultiplier
        if isBoostActive(at: now) {
            amount *= GameRules.boostMultiplier
        }
        addHash(amount)
    }

    @discardableResult
    func buy(_ hardware: OwnedHardware, quantity: Int = 1) -> Bool {
        let current = OwnedHardware(tier: hardware.tier, count: counts[hardware.id, default: 0])
        let cost = current.bulkCost(quantity)
        guard quantity > 0, quantity <= Int.max - current.count, !cost.isNaN, hash >= cost else { return false }
        hash -= cost
        counts[hardware.id, default: 0] += quantity
        save()
        return true
    }

    @discardableResult
    func activateBoost(at now: Date = Date()) -> Bool {
        guard !isBoostActive(at: now), cooldownRemaining(at: now) == 0 else {
            return false
        }
        boostEnd = now.addingTimeInterval(GameRules.boostDuration)
        save(at: now)
        return true
    }

    @discardableResult
    func prestige() -> Bool {
        let gained = pendingCores
        guard gained > 0 else { return false }
        prestigeCores += gained
        hash = 0
        counts = [:]
        runEarned = 0
        boostEnd = .distantPast
        offlineEarnings = 0
        lastTick = Date()
        save()
        return true
    }

    func resetProgress() {
        hash = 0
        counts = [:]
        runEarned = 0
        prestigeCores = 0
        boostEnd = .distantPast
        offlineEarnings = 0
        lastTick = Date()
        lastAutoSave = Date()
        save()
    }

    func setEffectsMuted(_ muted: Bool) {
        effectsMuted = muted
        save()
    }

    func clearOfflineEarnings() {
        offlineEarnings = 0
        save()
    }

    func isBoostActive(at now: Date = Date()) -> Bool {
        now < boostEnd
    }

    func boostRemaining(at now: Date = Date()) -> Int {
        max(0, Int(ceil(boostEnd.timeIntervalSince(now))))
    }

    func cooldownRemaining(at now: Date = Date()) -> Int {
        guard !isBoostActive(at: now) else { return 0 }
        let cooldownEnd = boostEnd.addingTimeInterval(GameRules.boostCooldown)
        return max(0, Int(ceil(cooldownEnd.timeIntervalSince(now))))
    }

    func save(at now: Date = Date()) {
        let state = SaveGame(
            version: 1,
            hash: hash,
            counts: counts,
            runEarned: runEarned,
            prestigeCores: prestigeCores,
            boostEnd: boostEnd.timeIntervalSince1970,
            lastSave: now.timeIntervalSince1970,
            effectsMuted: effectsMuted
        )
        guard let data = try? JSONEncoder().encode(state) else { return }
        defaults.set(data, forKey: saveKey)
    }

    private func load() {
        let now = Date()
        guard
            let data = defaults.data(forKey: saveKey),
            let loaded = try? JSONDecoder().decode(SaveGame.self, from: data)
        else {
            lastTick = now
            lastAutoSave = now
            save(at: now)
            return
        }

        hash = max(loaded.hash, 0)
        counts = loaded.counts.filter { !$0.key.isEmpty && $0.value >= 0 }
        runEarned = max(loaded.runEarned, 0)
        prestigeCores = max(loaded.prestigeCores, 0)
        boostEnd = Date(timeIntervalSince1970: loaded.boostEnd)
        effectsMuted = loaded.effectsMuted

        let lastSave = Date(timeIntervalSince1970: loaded.lastSave)
        let away = max(0, now.timeIntervalSince(lastSave))
        if away > 0 {
            let creditedSeconds = min(away, GameRules.offlineCap)
            let cappedEnd = lastSave.addingTimeInterval(creditedSeconds)
            let boosted = boostedSeconds(from: lastSave, to: cappedEnd, boostEnd: boostEnd)
            let earned = accrue(
                rate: effectiveRate,
                seconds: Int(creditedSeconds),
                boostedSeconds: boosted
            )
            addHash(earned)
            if away >= GameRules.offlineDialogMin, earned > 0 {
                offlineEarnings = earned
            }
        }

        lastTick = now
        lastAutoSave = now
        save(at: now)
    }

    private func addHash(_ amount: Decimal) {
        guard amount > 0 else { return }
        hash += amount
        runEarned += amount
    }
}

struct GameView: View {
    @StateObject private var store = GameStore()
    @Environment(\.scenePhase) private var scenePhase

    @State private var now = Date()
    @State private var showSettings = false
    @State private var showForkConfirm = false

    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                header

                FanButton {
                    feedback(.heavy, muted: store.effectsMuted)
                    store.manualMine(at: now)
                }
                .padding(.vertical, 8)

                actionButtons

                hardwareShop

                Button {
                    showSettings = true
                } label: {
                    Text("SETTINGS")
                        .font(.footnote.monospaced())
                        .foregroundStyle(.secondary)
                        .padding(.top, 4)
                }
                .accessibilityIdentifier("settings")
            }
            .padding(16)
        }
        .background(Color.minerBackground.ignoresSafeArea())
        .foregroundStyle(Color.terminalGreen)
        .onReceive(ticker) { date in
            now = date
            store.tick(at: date)
        }
        .onChange(of: scenePhase) { phase in
            if phase != .active {
                store.save()
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsView(store: store)
                .presentationDetents([.medium])
        }
        .alert("OFFLINE EARNINGS", isPresented: offlineAlertBinding) {
            Button("COLLECT") {
                store.clearOfflineEarnings()
            }
        } message: {
            Text("You generated \(formatHash(store.offlineEarnings)) Tokens while offline.")
        }
        .alert("FUNDING ROUND", isPresented: $showForkConfirm) {
            Button("CANCEL", role: .cancel) {}
            Button("PIVOT TO AGI") {
                feedback(.heavy, muted: store.effectsMuted)
                _ = store.prestige()
            }
        } message: {
            let next = Decimal(store.prestigeCores + store.pendingCores)
            let nextMultiplier = Decimal(1) + (next * GameRules.coreBonus)
            Text("Reset tokens and models to bank \(store.pendingCores) VC Billions.\n\nVC Billions are permanent and each adds +10% to all production. Your multiplier becomes x\(formatMultiplier(nextMultiplier)).")
        }
    }

    private var offlineAlertBinding: Binding<Bool> {
        Binding(
            get: { store.offlineEarnings > 0 },
            set: { visible in
                if !visible {
                    store.clearOfflineEarnings()
                }
            }
        )
    }

    private var header: some View {
        VStack(spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("AI BUBBLE")
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                    Text("\(formatHash(store.hash)) TOKENS")
                        .accessibilityIdentifier("tokenBalance")
                        .font(.system(size: 34, weight: .bold, design: .monospaced))
                        .minimumScaleFactor(0.6)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    Text("+\(formatHash(store.passiveRate)) Tokens/sec")
                        .accessibilityIdentifier("productionRate")
                        .font(.callout.monospaced())
                    Text("\(store.prestigeCores) VC Billions")
                        .accessibilityIdentifier("vcBillions")
                        .font(.caption.monospaced())
                        .foregroundStyle(Color.forkCyan)
                }
            }

            if store.prestigeCores > 0 {
                HStack {
                    Text("VC MULTIPLIER")
                    Spacer()
                    Text("x\(formatMultiplier(store.prestigeMultiplier))")
                }
                .font(.caption.monospaced())
                .foregroundStyle(Color.forkCyan)
                .padding(10)
                .background(Color.panel, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
    }

    private var actionButtons: some View {
        VStack(spacing: 10) {
            let boostActive = store.isBoostActive(at: now)
            let cooldown = store.cooldownRemaining(at: now)
            Button {
                if store.activateBoost(at: now) {
                    feedback(.medium, muted: store.effectsMuted)
                }
            } label: {
                Text(boostTitle(active: boostActive, cooldown: cooldown))
                    .font(.headline.monospaced())
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
            }
            .buttonStyle(FilledTerminalButtonStyle(color: .terminalGreen, disabled: boostActive || cooldown > 0))
            .disabled(boostActive || cooldown > 0)
            .accessibilityIdentifier("hypeCycle")

            Button {
                showForkConfirm = true
            } label: {
                Text(store.pendingCores > 0 ? "FUNDING ROUND (+\(store.pendingCores))" : "FUNDING ROUND (LOCKED)")
                    .font(.headline.monospaced())
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
            }
            .buttonStyle(FilledTerminalButtonStyle(color: .forkCyan, disabled: store.pendingCores == 0))
            .disabled(store.pendingCores == 0)
            .accessibilityIdentifier("fundingRound")
            Text(prestigePreview(store.runEarned))
                .font(.caption.monospaced())
                .accessibilityIdentifier("fundingPreview")
        }
    }

    @State private var bulk = 1

    private var hardwareShop: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("MODEL SHOP")
                .font(.headline.monospaced())

            Picker("Buy quantity", selection: $bulk) {
                Text("x1").tag(1).accessibilityIdentifier("bulk.1")
                Text("x10").tag(10).accessibilityIdentifier("bulk.10")
                Text("x100").tag(100).accessibilityIdentifier("bulk.100")
                Text("Max").tag(0).accessibilityIdentifier("bulk.0")
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("bulkSelector")

            ForEach(store.ownedHardware) { hardware in
                let quantity = bulk == 0 ? hardware.maxAffordable(store.hash) : bulk
                let cost = hardware.bulkCost(max(1, quantity))
                HardwareRow(
                    hardware: hardware,
                    canAfford: quantity > 0 && !cost.isNaN && store.hash >= cost,
                    cost: cost,
                    buyLabel: bulk == 0 ? "Max (\(quantity)) " : ""
                ) {
                    if store.buy(hardware, quantity: quantity) {
                        feedback(.light, muted: store.effectsMuted)
                    }
                }
            }
        }
    }

    private func boostTitle(active: Bool, cooldown: Int) -> String {
        if active {
            return "HYPE CYCLE ACTIVE: \(store.boostRemaining(at: now))s"
        }
        if cooldown > 0 {
            return "COOLDOWN: \(cooldown)s"
        }
        return "HYPE CYCLE (2x FOR 5 MIN)"
    }
}

struct FanButton: View {
    let onTap: () -> Void
    @State private var pressed = false

    var body: some View {
        Button {
            withAnimation(.easeOut(duration: 0.1)) {
                pressed = true
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                withAnimation(.spring(response: 0.25, dampingFraction: 0.55)) {
                    pressed = false
                }
            }
            onTap()
        } label: {
            ZStack {
                Circle()
                    .stroke(Color.panelBright, lineWidth: 6)
                    .background(Circle().fill(Color.panel))
                Circle()
                    .stroke(Color.terminalGreen, lineWidth: 2)
                    .padding(14)

                Image(systemName: "bubble.left")
                    .font(.system(size: 145, weight: .ultraLight))
                    .foregroundStyle(Color.terminalGreen)
                Text("Prompt")
                    .font(.title2.monospaced().bold())
                    .foregroundStyle(.secondary)
            }
            .frame(width: 210, height: 210)
            .scaleEffect(pressed ? 0.95 : 1)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Prompt")
        .accessibilityIdentifier("prompt")
        .accessibilityHint("Tap to prompt")
    }
}

struct HardwareRow: View {
    let hardware: OwnedHardware
    let canAfford: Bool
    let cost: Decimal
    let buyLabel: String
    let onBuy: () -> Void

    var body: some View {
        Button(action: onBuy) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(hardware.name)
                        .font(.body.monospaced().weight(.semibold))
                        .foregroundStyle(.white)
                    Text("+\(formatHash(hardware.baseRate)) Tokens/sec")
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                    Text("Owned: \(hardware.count)")
                        .font(.caption.monospaced())
                        .foregroundStyle(Color.terminalGreen)
                    let progress = milestoneProgress(hardware.count)
                    Text("\(hardware.count)/\(progress.next) · next x\(progress.factor) · x\(formatHash(milestoneMultiplier(hardware.count)))")
                        .font(.caption.monospaced())
                    ProgressView(value: Double(hardware.count - progress.previous), total: Double(progress.next - progress.previous))
                        .accessibilityIdentifier("milestone.\(hardware.id)")
                }

                Spacer()

                Text(buyLabel + formatHash(cost))
                    .font(.body.monospaced().bold())
                    .foregroundStyle(canAfford ? Color.terminalGreen : Color.red)
            }
            .padding(14)
            .background(Color.panel, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(canAfford ? Color.terminalGreen.opacity(0.4) : Color.clear, lineWidth: 1)
            }
            .opacity(canAfford ? 1 : 0.45)
        }
        .buttonStyle(.plain)
        .disabled(!canAfford)
        .accessibilityLabel("\(hardware.name), cost \(formatHash(cost)), owned \(hardware.count)")
        .accessibilityIdentifier("model.\(hardware.id)")
    }
}

struct SettingsView: View {
    @ObservedObject var store: GameStore
    @Environment(\.dismiss) private var dismiss
    @State private var confirmReset = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Effects") {
                    Toggle(
                        "Sound and haptics",
                        isOn: Binding(
                            get: { !store.effectsMuted },
                            set: { store.setEffectsMuted(!$0) }
                        )
                    )
                }

                Section("Save") {
                    Button("Reset Progress", role: .destructive) {
                        confirmReset = true
                    }
                    .accessibilityIdentifier("resetProgress")
                }

                Section("Disclosure") {
                    Text("Simulation game. No real AI was harmed. Not affiliated with any actual AI company. No backend, real money, investing, gambling, or financial advice.")
                        .font(.footnote)
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .alert("Reset Progress?", isPresented: $confirmReset) {
                Button("Cancel", role: .cancel) {}
                Button("Reset", role: .destructive) {
                    store.resetProgress()
                    dismiss()
                }
            } message: {
                Text("This wipes tokens, models, and VC Billions. It cannot be undone.")
            }
        }
    }
}

struct FilledTerminalButtonStyle: ButtonStyle {
    let color: Color
    let disabled: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(disabled ? color : .black)
            .background(disabled ? Color.panelBright : color, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .opacity(configuration.isPressed ? 0.78 : 1)
    }
}

extension Color {
    static let terminalGreen = Color(red: 0.0, green: 1.0, blue: 0.0)
    static let minerBackground = Color(red: 0.02, green: 0.02, blue: 0.02)
    static let panel = Color(red: 0.06, green: 0.06, blue: 0.06)
    static let panelBright = Color(red: 0.16, green: 0.16, blue: 0.16)
    static let forkCyan = Color(red: 0.0, green: 0.75, blue: 1.0)
}

func d(_ value: String) -> Decimal {
    Decimal(string: value, locale: Locale(identifier: "en_US_POSIX")) ?? 0
}

func powDecimal(_ base: Decimal, _ exponent: Int) -> Decimal {
    guard exponent > 0 else { return 1 }
    var power = exponent
    var factor = base
    var result: Decimal = 1
    while power > 0 {
        if power % 2 == 1 { result *= factor }
        power /= 2
        if power > 0 { factor *= factor }
        if result.isNaN || factor.isNaN { return .nan }
    }
    return result
}

func milestoneProgress(_ count: Int) -> (previous: Int, next: Int, factor: Int) {
    if count < 10 { return (0, 10, 2) }
    if count < 25 { return (10, 25, 2) }
    if count < 50 { return (25, 50, 2) }
    if count < 100 { return (50, 100, 3) }
    return (count / 100 * 100, (count / 100 + 1) * 100, 3)
}

func milestoneMultiplier(_ count: Int) -> Decimal {
    let doubles = [10, 25, 50].filter { count >= $0 }.count
    return powDecimal(2, doubles) * powDecimal(3, count / 100)
}

func nextPrestigeThreshold(_ earned: Decimal) -> Decimal {
    let next = Decimal(prestigeCoresFor(earned)) + 1
    return next * next * GameRules.prestigeThreshold
}

func prestigePreview(_ earned: Decimal) -> String {
    let coins = prestigeCoresFor(earned)
    let first = coins > 0
        ? "Pivot now for +\(coins) VC Billions (+\(NSDecimalNumber(decimal: Decimal(coins) * 10).stringValue)% production)"
        : "\(formatHash(max(0, GameRules.prestigeThreshold - earned))) Tokens this run needed for the first VC Billion"
    return first + "\n\(formatHash(max(0, nextPrestigeThreshold(earned) - earned))) Tokens this run needed for the next VC Billion"
}

func accrue(rate: Decimal, seconds: Int, boostedSeconds: Int) -> Decimal {
    guard seconds > 0, rate > 0 else { return 0 }
    let clampedBoost = min(max(boostedSeconds, 0), seconds)
    let normalSeconds = Decimal(seconds)
    let extraBoostSeconds = Decimal(clampedBoost) * (GameRules.boostMultiplier - 1)
    return rate * (normalSeconds + extraBoostSeconds)
}

func boostedSeconds(from start: Date, to end: Date, boostEnd: Date) -> Int {
    let overlapEnd = min(end, boostEnd)
    guard overlapEnd > start else { return 0 }
    return max(0, Int(overlapEnd.timeIntervalSince(start)))
}

func prestigeCoresFor(_ earned: Decimal) -> Int {
    guard earned >= GameRules.prestigeThreshold else { return 0 }
    var low = 0
    var high = Int.max
    while low < high {
        let mid = low + (high - low) / 2 + (high - low) % 2
        let threshold = Decimal(mid) * Decimal(mid) * GameRules.prestigeThreshold
        if threshold <= earned { low = mid } else { high = mid - 1 }
    }
    return low
}

func formatHash(_ value: Decimal) -> String {
    let double = NSDecimalNumber(decimal: max(value, 0)).doubleValue
    guard double.isFinite else { return "MAX" }
    if double < 1_000 {
        return "\(Int(floor(double)))"
    }

    let suffixes = ["", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"]
    var scaled = value
    var group = 0
    while scaled >= 1_000, group < suffixes.count - 1 {
        scaled /= 1_000
        group += 1
    }

    var exponent = 0
    var magnitude = value
    while magnitude >= 10 {
        magnitude /= 10
        exponent += 1
    }
    let scientific = exponent >= suffixes.count * 3
    var mantissa = value / powDecimal(10, scientific ? exponent : group * 3)
    var rounded = Decimal()
    NSDecimalRound(&rounded, &mantissa, 2, .down)
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    formatter.usesGroupingSeparator = false
    let text = formatter.string(from: NSDecimalNumber(decimal: rounded)) ?? "0.00"
    return text + (scientific ? "e\(exponent)" : suffixes[group])
}

func formatMultiplier(_ value: Decimal) -> String {
    let double = NSDecimalNumber(decimal: value).doubleValue
    guard double.isFinite else { return "MAX" }
    return String(format: "%.2f", double)
}

func feedback(_ style: UIImpactFeedbackGenerator.FeedbackStyle, muted: Bool) {
    #if canImport(UIKit)
    guard !muted else { return }
    UIImpactFeedbackGenerator(style: style).impactOccurred()
    #endif
}
