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
md5sums=('8f7cc0d33248cd6d71591d90e51ebbc0')

package() {
        cd "$srcdir/gnirehtet-rust-linux64"
        mkdir --parents $pkgdir/usr/bin
        cp gnirehtet $pkgdir/usr/bin
        mkdir --parents $pkgdir/opt/gnirehtet
        cp gnirehtet.apk $pkgdir/opt/gnirehtet
}
