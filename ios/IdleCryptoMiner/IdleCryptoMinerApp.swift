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
        tier.baseRate * Decimal(count)
    }
}

enum GameRules {
    static let hardware: [HardwareTier] = [
        HardwareTier(id: "cpu", name: "CPU Miner", baseCost: d("10"), baseRate: d("0.1")),
        HardwareTier(id: "gpu1", name: "GTX 1050", baseCost: d("120"), baseRate: d("1.5")),
        HardwareTier(id: "gpu2", name: "RTX 4090", baseCost: d("1500"), baseRate: d("24")),
        HardwareTier(id: "asic", name: "ASIC Miner", baseCost: d("18000"), baseRate: d("360")),
        HardwareTier(id: "rig", name: "Mining Rig", baseCost: d("220000"), baseRate: d("5500")),
        HardwareTier(id: "rack", name: "Server Rack", baseCost: d("2600000"), baseRate: d("83000")),
        HardwareTier(id: "datacenter", name: "Data Center", baseCost: d("32000000"), baseRate: d("1300000")),
        HardwareTier(id: "quantum", name: "Quantum Miner", baseCost: d("400000000"), baseRate: d("20000000"))
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
    func buy(_ hardware: OwnedHardware) -> Bool {
        guard hash >= hardware.currentCost else { return false }
        hash -= hardware.currentCost
        counts[hardware.id, default: 0] += 1
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
            Text("You mined \(formatHash(store.offlineEarnings)) Hash while away.")
        }
        .alert("HARD FORK", isPresented: $showForkConfirm) {
            Button("CANCEL", role: .cancel) {}
            Button("FORK") {
                feedback(.heavy, muted: store.effectsMuted)
                _ = store.prestige()
            }
        } message: {
            let next = Decimal(store.prestigeCores + store.pendingCores)
            let nextMultiplier = Decimal(1) + (next * GameRules.coreBonus)
            Text("Reset hash and hardware to bank \(store.pendingCores) core(s). Cores are permanent. New multiplier: x\(formatMultiplier(nextMultiplier)).")
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
                    Text("IDLE CRYPTO MINER")
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                    Text("\(formatHash(store.hash)) HASH")
                        .font(.system(size: 34, weight: .bold, design: .monospaced))
                        .minimumScaleFactor(0.6)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    Text("+\(formatHash(store.effectiveRate))/sec")
                        .font(.callout.monospaced())
                    Text("\(store.prestigeCores) cores")
                        .font(.caption.monospaced())
                        .foregroundStyle(Color.forkCyan)
                }
            }

            if store.prestigeCores > 0 {
                HStack {
                    Text("FORK MULTIPLIER")
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

            Button {
                showForkConfirm = true
            } label: {
                Text(store.pendingCores > 0 ? "HARD FORK (+\(store.pendingCores) CORES)" : "HARD FORK (LOCKED)")
                    .font(.headline.monospaced())
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
            }
            .buttonStyle(FilledTerminalButtonStyle(color: .forkCyan, disabled: store.pendingCores == 0))
            .disabled(store.pendingCores == 0)
        }
    }

    private var hardwareShop: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("HARDWARE SHOP")
                .font(.headline.monospaced())

            ForEach(store.ownedHardware) { hardware in
                HardwareRow(
                    hardware: hardware,
                    canAfford: store.hash >= hardware.currentCost
                ) {
                    if store.buy(hardware) {
                        feedback(.light, muted: store.effectsMuted)
                    }
                }
            }
        }
    }

    private func boostTitle(active: Bool, cooldown: Int) -> String {
        if active {
            return "OVERCLOCK ACTIVE: \(store.boostRemaining(at: now))s"
        }
        if cooldown > 0 {
            return "COOLDOWN: \(cooldown)s"
        }
        return "OVERCLOCK (2x FOR 5 MIN)"
    }
}

struct FanButton: View {
    let onTap: () -> Void
    @State private var rotation = 0.0
    @State private var pressed = false

    var body: some View {
        Button {
            withAnimation(.linear(duration: 0.38)) {
                rotation += 360
            }
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

                ZStack {
                    ForEach(0..<3, id: \.self) { index in
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(Color.panelBright)
                            .frame(width: 34, height: 92)
                            .offset(y: -42)
                            .rotationEffect(.degrees(Double(index) * 120))
                    }
                }
                .rotationEffect(.degrees(rotation))

                Circle()
                    .fill(Color.minerBackground)
                    .frame(width: 78, height: 78)
                Text("GPU")
                    .font(.title2.monospaced().bold())
                    .foregroundStyle(.secondary)
            }
            .frame(width: 210, height: 210)
            .scaleEffect(pressed ? 0.95 : 1)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Mine hash")
        .accessibilityHint("Taps the GPU fan to mine hash")
    }
}

struct HardwareRow: View {
    let hardware: OwnedHardware
    let canAfford: Bool
    let onBuy: () -> Void

    var body: some View {
        Button(action: onBuy) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(hardware.name)
                        .font(.body.monospaced().weight(.semibold))
                        .foregroundStyle(.white)
                    Text("+\(formatHash(hardware.baseRate))/sec each")
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                    Text("Owned: \(hardware.count)")
                        .font(.caption.monospaced())
                        .foregroundStyle(Color.terminalGreen)
                }

                Spacer()

                Text(formatHash(hardware.currentCost))
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
        .accessibilityLabel("\(hardware.name), cost \(formatHash(hardware.currentCost)), owned \(hardware.count)")
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
                }

                Section("Disclosure") {
                    Text("Simulation game. Not real cryptocurrency mining, money, investing, gambling, or financial advice.")
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
                Text("This wipes hash, hardware, and prestige cores. It cannot be undone.")
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
    return (0..<exponent).reduce(Decimal(1)) { result, _ in
        result * base
    }
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
    let ratio = NSDecimalNumber(decimal: earned / GameRules.prestigeThreshold).doubleValue
    guard ratio.isFinite, ratio >= 1 else { return 0 }
    return min(Int.max, max(0, Int(floor(sqrt(ratio)))))
}

func formatHash(_ value: Decimal) -> String {
    let double = NSDecimalNumber(decimal: max(value, 0)).doubleValue
    guard double.isFinite else { return "MAX" }
    if double < 1_000 {
        return "\(Int(floor(double)))"
    }

    let suffixes = ["", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"]
    var scaled = double
    var group = 0
    while scaled >= 1_000, group < suffixes.count - 1 {
        scaled /= 1_000
        group += 1
    }

    if group < suffixes.count - 1 {
        return String(format: "%.2f%@", scaled, suffixes[group])
    }

    let exponent = Int(floor(log10(double)))
    let mantissa = double / pow(10, Double(exponent))
    return String(format: "%.2fe%d", mantissa, exponent)
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
