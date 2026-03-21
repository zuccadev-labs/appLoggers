Pod::Spec.new do |s|
  s.name             = 'AppLogger'
  s.version          = '0.1.1-alpha.3'
  s.summary          = 'Kotlin Multiplatform SDK for structured technical telemetry.'
  s.description      = <<-DESC
    AppLogger is a Kotlin Multiplatform SDK that captures structured logs and metrics
    from Android, iOS, and JVM applications. Events are delivered to Supabase (PostgreSQL)
    with automatic batching, offline buffering, and crash capture.
  DESC

  s.homepage         = 'https://github.com/zuccadev-labs/appLoggers'
  s.license          = { :type => 'MIT', :file => 'LICENSE' }
  s.author           = { 'DevZucca' => 'devzucca@github.com' }
  s.source           = { :http => "https://github.com/zuccadev-labs/appLoggers/releases/download/v#{s.version}/AppLogger.xcframework.zip" }

  s.ios.deployment_target  = '14.0'
  s.tvos.deployment_target = '14.0'

  s.static_framework = true
  s.vendored_frameworks = 'AppLogger.xcframework'

  # The XCFramework is produced by the Kotlin/Native Gradle task:
  #   ./gradlew :logger-core:assembleXCFramework
  #
  # For local development, use:
  #   pod 'AppLogger', :path => '../appLoggers'
end
