# Maintainer: Shreyansh Khajanchi <shreyansh_k@live.com>

pkgname=gnirehtet
pkgver=2.1
pkgrel=1
pkgdesc="Gnirehtet provides reverse tethering for Android"
arch=('x86_64')
url="https://github.com/Genymobile/gnirehtet"
license=('Apache-2.0')
depends=('android-tools')
source=("https://github.com/Genymobile/gnirehtet/releases/download/v$pkgver/gnirehtet-rust-linux64-v$pkgver.zip")
sha256sums=('0f2a694611270eaf8a18af9ebf713932e05e4be75d0a38774154804da4d60d4d')

package() {
        cd "$srcdir/gnirehtet-rust-linux64"
        mkdir --parents $pkgdir/usr/bin
        cp gnirehtet $pkgdir/usr/bin
        mkdir --parents $pkgdir/usr/share/gnirehtet
        cp gnirehtet.apk $pkgdir/usr/share/gnirehtet
}
