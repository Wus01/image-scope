import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
  type KeyboardEvent,
} from 'react'
import './App.css'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')
const CLIENT_ID_KEY = 'image-scope-client-id'
const MAX_FILE_SIZE = 20 * 1024 * 1024
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif']

type ImageItem = {
  imageId: string
  originalName: string
  mimeType: string
  originalSize: number
  previewSize: number | null
  reductionRatePercent: number | null
  width: number
  height: number
  megapixels: number
  estimatedMemoryBytes: number
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REJECTED'
  message: string | null
  previewUrl: string | null
  createdAt: string
}

type UploadResponse = {
  imageId: string
  clientId: string
  originalName: string
  mimeType: string
  originalSize: number
  previewSize: number | null
  reductionRatePercent: number | null
  width: number
  height: number
  megapixels: number
  estimatedMemoryBytes: number
  status: ImageItem['status']
  message: string
}

type ErrorPayload = {
  detail?: string
  message?: string
  error?: string
}

type DownloadType = 'preview' | 'original'

const statusLabel: Record<ImageItem['status'], string> = {
  PROCESSING: '처리 중',
  COMPLETED: '완료',
  FAILED: '처리 실패',
  REJECTED: '거부',
}

function getClientId() {
  const storedClientId = localStorage.getItem(CLIENT_ID_KEY)
  if (storedClientId) return storedClientId

  const newClientId = crypto.randomUUID()
  localStorage.setItem(CLIENT_ID_KEY, newClientId)
  return newClientId
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 ** 2).toFixed(1)} MB`
}

function formatReductionRate(value: number | null) {
  return value === null ? '-' : `${value.toFixed(2)}%`
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

async function getErrorMessage(response: Response) {
  const payload = (await response.json().catch(() => null)) as ErrorPayload | null
  return payload?.detail ?? payload?.message ?? payload?.error ?? `요청에 실패했습니다. (${response.status})`
}

function UploadIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 16V4m0 0L7.5 8.5M12 4l4.5 4.5M5 14v4.25A1.75 1.75 0 0 0 6.75 20h10.5A1.75 1.75 0 0 0 19 18.25V14" />
    </svg>
  )
}

function RefreshIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M19.5 8A8 8 0 1 0 20 14m-.5-6V3.5m0 4.5H15" />
    </svg>
  )
}

function DeleteIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 7h16M9 7V4.75h6V7m3 0-.75 12.25A1.75 1.75 0 0 1 15.5 21h-7a1.75 1.75 0 0 1-1.75-1.75L6 7m4 4v6m4-6v6" />
    </svg>
  )
}

function DownloadIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 4v12m0 0 4.5-4.5M12 16l-4.5-4.5M5 20h14" />
    </svg>
  )
}

function getPreviewFileName(originalName: string, mimeType: string) {
  const baseName = originalName.replace(/\.[^/.]+$/, '') || 'image'
  const extension = mimeType === 'image/png' ? 'png' : 'jpg'
  return `${baseName}-preview.${extension}`
}

function App() {
  const [clientId] = useState(getClientId)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [images, setImages] = useState<ImageItem[]>([])
  const [previewUrls, setPreviewUrls] = useState<Record<string, string>>({})
  const [lastResult, setLastResult] = useState<UploadResponse | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [deletingImageId, setDeletingImageId] = useState<string | null>(null)
  const [downloadImage, setDownloadImage] = useState<ImageItem | null>(null)
  const [downloadingType, setDownloadingType] = useState<DownloadType | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isApiOnline, setIsApiOnline] = useState<boolean | null>(null)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const loadImages = useCallback(async () => {
    setIsLoading(true)
    setError(null)

    try {
      const response = await fetch(`${API_BASE_URL}/api/images`, {
        headers: { 'X-Client-Id': clientId },
      })
      setIsApiOnline(true)

      if (!response.ok) throw new Error(await getErrorMessage(response))
      setImages((await response.json()) as ImageItem[])
    } catch (requestError) {
      if (requestError instanceof TypeError) setIsApiOnline(false)
      setError(requestError instanceof Error ? requestError.message : '이미지 목록을 불러오지 못했습니다.')
    } finally {
      setIsLoading(false)
    }
  }, [clientId])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadImages()
    }, 0)

    return () => window.clearTimeout(timeoutId)
  }, [loadImages])

  useEffect(() => {
    let cancelled = false
    const createdUrls: string[] = []

    async function loadPreviews() {
      const entries = await Promise.all(
        images
          .filter((image) => image.previewUrl)
          .map(async (image) => {
            try {
              const response = await fetch(`${API_BASE_URL}${image.previewUrl}`, {
                headers: { 'X-Client-Id': clientId },
              })
              if (!response.ok) return null

              const objectUrl = URL.createObjectURL(await response.blob())
              if (cancelled) {
                URL.revokeObjectURL(objectUrl)
                return null
              }

              createdUrls.push(objectUrl)
              return [image.imageId, objectUrl] as const
            } catch {
              return null
            }
          }),
      )

      if (!cancelled) {
        setPreviewUrls(Object.fromEntries(entries.filter((entry) => entry !== null)))
      }
    }

    void loadPreviews()

    return () => {
      cancelled = true
      createdUrls.forEach((url) => URL.revokeObjectURL(url))
    }
  }, [clientId, images])

  function chooseFile(file?: File) {
    setLastResult(null)
    setError(null)

    if (!file) return
    if (!ALLOWED_TYPES.includes(file.type)) {
      setSelectedFile(null)
      setError('JPG, PNG, GIF 이미지만 선택할 수 있어요.')
      return
    }
    if (file.size > MAX_FILE_SIZE) {
      setSelectedFile(null)
      setError('파일 크기는 20MB를 초과할 수 없어요.')
      return
    }

    setSelectedFile(file)
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    chooseFile(event.target.files?.[0])
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragging(false)
    chooseFile(event.dataTransfer.files?.[0])
  }

  function handleDropZoneKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      fileInputRef.current?.click()
    }
  }

  async function uploadImage() {
    if (!selectedFile || isUploading) return

    setIsUploading(true)
    setError(null)
    setLastResult(null)

    const formData = new FormData()
    formData.append('file', selectedFile)

    try {
      const response = await fetch(`${API_BASE_URL}/api/images`, {
        method: 'POST',
        headers: { 'X-Client-Id': clientId },
        body: formData,
      })
      setIsApiOnline(true)

      const payload = (await response.json()) as UploadResponse | ErrorPayload
      if (!response.ok) {
        const requestError = payload as ErrorPayload
        throw new Error(requestError.detail ?? requestError.message ?? requestError.error ?? '이미지 업로드에 실패했습니다.')
      }

      setLastResult(payload as UploadResponse)
      setSelectedFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
      await loadImages()
    } catch (requestError) {
      if (requestError instanceof TypeError) setIsApiOnline(false)
      setError(requestError instanceof Error ? requestError.message : '이미지 업로드에 실패했습니다.')
    } finally {
      setIsUploading(false)
    }
  }

  async function deleteImage(image: ImageItem) {
    const confirmed = window.confirm(
      `"${image.originalName}"을(를) 삭제할까요?\n원본과 미리보기가 함께 삭제되며 복구할 수 없어요.`,
    )
    if (!confirmed) return

    setDeletingImageId(image.imageId)
    setError(null)

    try {
      const response = await fetch(`${API_BASE_URL}/api/images/${image.imageId}`, {
        method: 'DELETE',
        headers: { 'X-Client-Id': clientId },
      })
      setIsApiOnline(true)

      if (!response.ok) throw new Error(await getErrorMessage(response))

      setLastResult((result) => result?.imageId === image.imageId ? null : result)
      await loadImages()
    } catch (requestError) {
      if (requestError instanceof TypeError) setIsApiOnline(false)
      setError(requestError instanceof Error ? requestError.message : '이미지 삭제에 실패했습니다.')
    } finally {
      setDeletingImageId(null)
    }
  }

  async function downloadSelectedImage(type: DownloadType) {
    if (!downloadImage || downloadingType) return

    setDownloadingType(type)
    setError(null)

    try {
      const response = await fetch(
        `${API_BASE_URL}/api/images/${downloadImage.imageId}/${type}`,
        { headers: { 'X-Client-Id': clientId } },
      )
      setIsApiOnline(true)

      if (!response.ok) throw new Error(await getErrorMessage(response))

      const blob = await response.blob()
      const objectUrl = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = objectUrl
      anchor.download = type === 'original'
        ? downloadImage.originalName
        : getPreviewFileName(downloadImage.originalName, blob.type)
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(objectUrl)
      setDownloadImage(null)
    } catch (requestError) {
      if (requestError instanceof TypeError) setIsApiOnline(false)
      setError(requestError instanceof Error ? requestError.message : '이미지 다운로드에 실패했습니다.')
    } finally {
      setDownloadingType(null)
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="ImageScope 홈">
          <span className="brand-mark" aria-hidden="true"><span /></span>
          <span>ImageScope</span>
        </a>
        <span className={`service-state ${isApiOnline === false ? 'is-offline' : isApiOnline === null ? 'is-checking' : ''}`}>
          <i /> {isApiOnline === false ? 'Service offline' : isApiOnline === null ? 'Checking service' : 'Service online'}
        </span>
      </header>

      <main id="top">
        <section className="workspace" aria-labelledby="page-title">
          <div className="intro-copy">
            <p className="eyebrow">SAFE IMAGE PIPELINE</p>
            <h1 id="page-title">고해상도 이미지를<br /><em>가볍게 확인하세요.</em></h1>
            <p className="intro-description">
              해상도와 예상 디코딩 메모리를 먼저 분석하고,
              원본을 유지한 채 빠른 미리보기를 생성합니다.
            </p>
            <div className="trust-row" aria-label="지원 정보">
              <span>JPG · PNG · GIF</span>
              <span>최대 20MB</span>
              <span>원본 별도 보관</span>
            </div>
          </div>

          <div className="upload-panel">
            <div
              className={`drop-zone${isDragging ? ' is-dragging' : ''}${selectedFile ? ' has-file' : ''}`}
              onClick={() => fileInputRef.current?.click()}
              onKeyDown={handleDropZoneKeyDown}
              onDragEnter={(event) => { event.preventDefault(); setIsDragging(true) }}
              onDragOver={(event) => event.preventDefault()}
              onDragLeave={() => setIsDragging(false)}
              onDrop={handleDrop}
              role="button"
              tabIndex={0}
              aria-label="이미지 파일 선택"
            >
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/gif"
                onChange={handleFileChange}
                hidden
              />
              <span className="upload-icon"><UploadIcon /></span>
              {selectedFile ? (
                <>
                  <strong>{selectedFile.name}</strong>
                  <span>{formatBytes(selectedFile.size)} · 클릭해서 다른 파일 선택</span>
                </>
              ) : (
                <>
                  <strong>이미지를 이곳에 놓아주세요</strong>
                  <span>또는 클릭해서 파일 선택</span>
                </>
              )}
            </div>
            <button
              className="primary-button"
              type="button"
              onClick={uploadImage}
              disabled={!selectedFile || isUploading}
            >
              {isUploading ? <><span className="spinner" /> 이미지 분석 중</> : '분석하고 미리보기 만들기'}
            </button>
            <p className="upload-note">고해상도 원본은 그대로 보관하고, 미리보기는 저메모리 방식으로 안전하게 생성해요.</p>
          </div>
        </section>

        <div className="feedback-region" aria-live="polite">
          {error && <div className="alert error-alert"><strong>확인해주세요</strong><span>{error}</span></div>}
          {lastResult && (
            <section className={`result-card status-${lastResult.status.toLowerCase()}`} aria-labelledby="result-title">
              <div className="result-heading">
                <div>
                  <p className="section-kicker">LATEST RESULT</p>
                  <h2 id="result-title">{lastResult.originalName}</h2>
                </div>
                <span className="status-badge">{statusLabel[lastResult.status]}</span>
              </div>
              <div className="metrics-grid">
                <div><span>해상도</span><strong>{lastResult.width.toLocaleString()} × {lastResult.height.toLocaleString()}</strong></div>
                <div><span>메가픽셀</span><strong>{lastResult.megapixels.toFixed(2)} MP</strong></div>
                <div><span>원본 파일 용량</span><strong>{formatBytes(lastResult.originalSize)}</strong></div>
                <div><span>원본 디코딩 시 예상 RAM</span><strong>{formatBytes(lastResult.estimatedMemoryBytes)}</strong></div>
                <div><span>미리보기 파일 용량</span><strong>{lastResult.previewSize === null ? '-' : formatBytes(lastResult.previewSize)}</strong></div>
                <div><span>원본 대비 전송량 절감률</span><strong>{formatReductionRate(lastResult.reductionRatePercent)}</strong></div>
              </div>
              <p className="result-message">{lastResult.message}</p>
            </section>
          )}
        </div>

        <section className="library" aria-labelledby="library-title">
          <div className="section-heading">
            <div>
              <p className="section-kicker">RECENT IMAGES</p>
              <h2 id="library-title">저장된 이미지</h2>
            </div>
            <button className="refresh-button" type="button" onClick={() => void loadImages()} disabled={isLoading}>
              <RefreshIcon /> 새로고침
            </button>
          </div>

          {isLoading ? (
            <div className="loading-grid" aria-label="이미지 목록 로딩 중">
              {[0, 1, 2].map((item) => <div className="skeleton-card" key={item}><span /><i /><i /></div>)}
            </div>
          ) : images.length === 0 ? (
            <div className="empty-state">
              <span className="empty-mark" aria-hidden="true" />
              <h3>아직 저장된 이미지가 없어요</h3>
              <p>첫 이미지를 업로드하면 분석 결과와 미리보기가 여기에 표시됩니다.</p>
            </div>
          ) : (
            <div className="image-grid">
              {images.map((image) => (
                <article className="image-card" key={image.imageId}>
                  <div className="preview-frame">
                    {previewUrls[image.imageId] ? (
                      <img src={previewUrls[image.imageId]} alt={`${image.originalName} 미리보기`} />
                    ) : (
                      <div className={`preview-placeholder status-${image.status.toLowerCase()}`}>
                        <span>{statusLabel[image.status]}</span>
                      </div>
                    )}
                    <span className={`card-status status-${image.status.toLowerCase()}`}>{statusLabel[image.status]}</span>
                  </div>
                  <div className="card-content">
                    <h3 title={image.originalName}>{image.originalName}</h3>
                    <p>{image.width.toLocaleString()} × {image.height.toLocaleString()} · {image.megapixels.toFixed(2)} MP</p>
                    <div className="card-meta">
                      <span>원본 {formatBytes(image.originalSize)}</span>
                      <span>미리보기 {image.previewSize === null ? '-' : formatBytes(image.previewSize)}</span>
                      <span>절감률 {formatReductionRate(image.reductionRatePercent)}</span>
                      <time dateTime={image.createdAt}>{formatDate(image.createdAt)}</time>
                    </div>
                    {image.status !== 'COMPLETED' && image.message && <p className="card-message">{image.message}</p>}
                    <div className="card-actions">
                      <button
                        className="download-button"
                        type="button"
                        onClick={() => setDownloadImage(image)}
                        disabled={image.status === 'REJECTED'}
                        aria-label={`${image.originalName} 다운로드`}
                      >
                        <DownloadIcon /> 다운로드
                      </button>
                      <button
                        className="delete-button"
                        type="button"
                        onClick={() => void deleteImage(image)}
                        disabled={deletingImageId !== null}
                        aria-label={`${image.originalName} 삭제`}
                      >
                        {deletingImageId === image.imageId ? <span className="delete-spinner" /> : <DeleteIcon />}
                        {deletingImageId === image.imageId ? '삭제 중' : '삭제'}
                      </button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </main>

      {downloadImage && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => !downloadingType && setDownloadImage(null)}>
          <section
            className="download-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="download-dialog-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <p className="section-kicker">DOWNLOAD IMAGE</p>
            <h2 id="download-dialog-title">어떤 이미지를 저장할까요?</h2>
            <p>{downloadImage.originalName}</p>
            <div className="download-options">
              <button
                type="button"
                onClick={() => void downloadSelectedImage('preview')}
                disabled={!downloadImage.previewUrl || downloadingType !== null}
              >
                <strong>미리보기</strong>
                <span>압축된 가벼운 이미지</span>
              </button>
              <button
                type="button"
                onClick={() => void downloadSelectedImage('original')}
                disabled={downloadingType !== null}
              >
                <strong>원본</strong>
                <span>업로드한 원본 이미지</span>
              </button>
            </div>
            <button
              className="modal-cancel-button"
              type="button"
              onClick={() => setDownloadImage(null)}
              disabled={downloadingType !== null}
            >
              {downloadingType ? '다운로드 중...' : '취소'}
            </button>
          </section>
        </div>
      )}

      <footer>
        <span>ImageScope</span>
        <p>원본과 미리보기를 분리해 안전하고 빠르게 처리합니다.</p>
      </footer>
    </div>
  )
}

export default App
