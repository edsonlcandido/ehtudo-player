import SwiftUI

/// Katalog yüklemesi başarısız olduğunda üç sekmenin (Canlı/Film/Dizi) boş ekran yerine
/// gösterdiği ortak hata durumu: mesaj + satır içi "Tekrar Dene". Eskiden hata yalnızca
/// Dashboard'daki tek seferlik alert'te görünüyor, OK'a basan kullanıcı çıkışsız boş
/// sekmelerle kalıyordu (tek kurtuluş Ayarlar → Tümünü Yenile'ydi).
struct CatalogLoadErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "wifi.exclamationmark")
                .font(.largeTitle)
                .foregroundColor(.secondary)
            Text(L("catalog.load_failed.title"))
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .lineLimit(4)
                .padding(.horizontal, 32)
            Button {
                onRetry()
            } label: {
                Label(L("common.try_again"), systemImage: "arrow.clockwise")
            }
            .buttonStyle(.borderedProminent)
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
