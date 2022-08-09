#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint interactive_webview.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'interactive_webview'
  s.version          = '1.0.0'
  s.summary          = 'Plugin that allow Flutter to communicate with a native WebView.'
  s.description      = <<-DESC
A new Flutter plugin project.
                       DESC
  s.homepage         = 'https://github.com/duyduong/interactive_webview'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Dao Duy Duong' => 'dduy.duong@gmail.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '9.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'
end
