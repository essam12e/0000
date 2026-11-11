package main

// نماذج البيانات المشتركة بين المحرك والواجهة (JSON).

type Quality struct {
	ID       string `json:"id"`
	Label    string `json:"label"`
	Badge    string `json:"badge,omitempty"`
	Detail   string `json:"detail"`
	Size     int64  `json:"size,omitempty"`
	Approx   bool   `json:"approx"`
	Selector string `json:"selector"`
	Side     int    `json:"side"`
}

type PlaylistItem struct {
	Index     int    `json:"index"`
	Title     string `json:"title"`
	Thumbnail string `json:"thumbnail,omitempty"`
	Duration  int64  `json:"duration,omitempty"`
	URL       string `json:"url,omitempty"` // رابط العنصر (لتحليل عيّنة من القائمة وتقدير الحجم)
}

type DirectFile struct {
	URL  string `json:"url"`
	Name string `json:"name"`
	Mime string `json:"mime,omitempty"`
	Size int64  `json:"size,omitempty"`
	Kind string `json:"kind"`
}

type AudioTrack struct {
	Lang     string `json:"lang"`
	Name     string `json:"name"`
	Original bool   `json:"original"`
}

type SubTrack struct {
	Lang string `json:"lang"`
	Name string `json:"name"`
	Auto bool   `json:"auto"`
	From string `json:"from,omitempty"` // لغة المصدر إن كانت مترجمة آلياً من ترجمة أخرى
	Src  string `json:"src,omitempty"`  // رمز الترجمة الأصلية التي تُنزَّل معها كبديل
	Note string `json:"note"`           // مصدر الترجمة كما يُعرض للمستخدم
}

type MediaInfo struct {
	SourceURL   string         `json:"sourceUrl"`
	Title       string         `json:"title"`
	Uploader    string         `json:"uploader,omitempty"`
	Thumbnail   string         `json:"thumbnail,omitempty"`
	Duration    int64          `json:"duration,omitempty"`
	Platform    string         `json:"platform,omitempty"`
	Options     []Quality      `json:"options"`
	HasAudio    bool           `json:"hasAudio"`
	AudioSize   int64          `json:"audioSize,omitempty"`
	IsPlaylist  bool           `json:"isPlaylist"`
	Items       []PlaylistItem `json:"items"`
	Direct      []DirectFile   `json:"direct"`
	ViaGallery  bool           `json:"viaGallery"`
	AudioTracks []AudioTrack   `json:"audioTracks"`
	Subtitles   []SubTrack     `json:"subtitles"`
	MediaKey    string         `json:"mediaKey,omitempty"`
	Duplicate   *HistoryEntry  `json:"duplicate,omitempty"`
}

type Spec struct {
	URL         string `json:"url"`
	Title       string `json:"title"`
	Thumbnail   string `json:"thumbnail,omitempty"`
	Mode        string `json:"mode"` // video | audio | direct | gallery
	Selector    string `json:"selector,omitempty"`
	Label       string `json:"label"`
	AudioFormat string `json:"audioFormat,omitempty"`
	Items       []int  `json:"items,omitempty"`
	DirectName  string `json:"directName,omitempty"`
	Expected    int64  `json:"expected,omitempty"`
	Duration    int64  `json:"duration,omitempty"`
	AudioLang   string `json:"audioLang,omitempty"`
	SubLang     string `json:"subLang,omitempty"`
	SubSrc      string `json:"subSrc,omitempty"`
	SubEmbed    bool   `json:"subEmbed"`
	TrimStart   *int64 `json:"trimStart,omitempty"`
	TrimEnd     *int64 `json:"trimEnd,omitempty"`
	MediaKey    string `json:"mediaKey,omitempty"`
	Platform    string `json:"platform,omitempty"`
}

type Task struct {
	ID        string  `json:"id"`
	Spec      Spec    `json:"spec"`
	State     string  `json:"state"` // queued | running | paused | failed
	Progress  float64 `json:"progress"`
	Speed     string  `json:"speed,omitempty"`
	ETA       int64   `json:"eta,omitempty"`
	Stage     string  `json:"stage,omitempty"`
	Error     string  `json:"error,omitempty"`
	CreatedAt int64   `json:"createdAt"`
}

type LibItem struct {
	ID        string `json:"id"`
	Path      string `json:"path"`
	Title     string `json:"title"`
	Kind      string `json:"kind"` // video | audio | image | other
	Mime      string `json:"mime"`
	Ext       string `json:"ext"`
	Size      int64  `json:"size"`
	Duration  int64  `json:"duration,omitempty"`
	Thumb     string `json:"thumb,omitempty"`
	SourceURL string `json:"sourceUrl,omitempty"`
	AddedAt   int64  `json:"addedAt"`
}

type HistoryEntry struct {
	ID        string `json:"id"`
	URL       string `json:"url"`
	Key       string `json:"key,omitempty"`
	Title     string `json:"title"`
	Thumbnail string `json:"thumbnail,omitempty"`
	Label     string `json:"label"`
	Platform  string `json:"platform,omitempty"`
	At        int64  `json:"at"`
}

type Settings struct {
	DefaultQuality int    `json:"defaultQuality"` // 0 الأعلى، -1 صوت فقط، وإلا الارتفاع
	AudioFormat    string `json:"audioFormat"`
	MaxParallel    int    `json:"maxParallel"`
	SaveDir        string `json:"saveDir"`        // فارغ = التنزيلات\داونلودر
	AutoSave       bool   `json:"autoSave"`       // حفظ تلقائي في الجهاز بعد التحميل
	CookiesBrowser string `json:"cookiesBrowser"` // chrome | edge | firefox | brave | opera | ""
	CookiesFile    string `json:"cookiesFile"`
	LastEngineUpd  int64  `json:"lastEngineUpdate"`
}

// AppError خطأ مصنَّف يُعرض برسالة عربية واضحة.
type AppError struct {
	Type   string `json:"type"` // unsupported | private | offline | engine | generic
	Detail string `json:"detail,omitempty"`
}

func (e *AppError) Error() string { return e.Type + ": " + e.Detail }
